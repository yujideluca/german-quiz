package dev.yuji.quiz.domain

import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.StudyLog
import dev.yuji.quiz.debug.debug
import kotlin.random.Random

/** What happened when the user answered the current card. */
data class AnswerRecord(
    val chosen: String,
    val correct: Boolean,
    val grade: ReviewGrade,
    val responseMillis: Long,
    val before: CardProgress,
    val after: CardProgress,
)

/** Immutable state of one study session. Transitions live in [StudyEngine]. */
data class StudySession(
    val mode: GameMode,
    /** Remaining cards; the head is the current card. Learning cards re-enter at the tail. */
    val queue: List<Card>,
    val progressById: Map<String, CardProgress>,
    val shownAtMillis: Long,
    val choiceSeed: Long,
    val lastAnswer: AnswerRecord? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
) {
    val currentCard: Card? get() = queue.firstOrNull()
    val isFinished: Boolean get() = queue.isEmpty()
    val isAwaitingAdvance: Boolean get() = lastAnswer != null
    val answeredCount: Int get() = correctCount + wrongCount
}

/** Pure state transitions for a [StudySession]. */
object StudyEngine {

    fun start(
        mode: GameMode,
        queue: StudyQueue,
        progressById: Map<String, CardProgress>,
        nowMillis: Long,
        choiceSeed: Long,
    ) = StudySession(mode, queue.cards, progressById, nowMillis, choiceSeed)

    /**
     * Choices in presentation order; stable for a given card, seed and repetition count.
     * While the answer feedback is showing, the count from *before* the answer is used, because
     * answering increments the repetitions and would otherwise reshuffle the buttons under the player's finger.
     */
    fun choicesFor(session: StudySession): List<String> {
        val card = session.currentCard ?: return emptyList()
        if (!card.shuffleChoices) return card.choices
        val reps = session.lastAnswer?.before?.repetitions ?: session.progressById[card.id]?.repetitions ?: 0
        val seed = session.choiceSeed xor card.id.hashCode().toLong() xor (reps.toLong() shl 32)
        return card.choices.shuffled(Random(seed))
    }

    /** Records an answer. Ignored if finished, already answered, or [choice] is not offered. */
    fun answer(
        session: StudySession,
        choice: String,
        nowMillis: Long,
        schedulerConfig: SchedulerConfig,
        graderConfig: GraderConfig,
        log: StudyLog,
    ): StudySession {
        val card = session.currentCard ?: return session
        if (session.isAwaitingAdvance || choice !in card.choices) return session

        val before = session.progressById[card.id]
            ?: SpacedRepetitionScheduler.freshProgress(card.id, schedulerConfig)
        val correct = choice == card.correctChoice
        val responseMillis = (nowMillis - session.shownAtMillis).coerceAtLeast(0)
        val grade = AnswerGrader.grade(correct, responseMillis, before.phase, graderConfig)
        val after = SpacedRepetitionScheduler.scheduleNext(before, grade, nowMillis, schedulerConfig)

        val comesBackThisSession = (after.phase == CardPhase.LEARNING || after.phase == CardPhase.RELEARNING) &&
            after.dueAtMillis <= nowMillis + schedulerConfig.learnAheadMinutes * MINUTE_MILLIS
        log.debug(LogChannel.SESSION) {
            "answer ${card.id}: '$choice' correct=$correct ${responseMillis}ms -> $grade | " +
                "${before.phase}->${after.phase} step=${after.learningStep} ease=${"%.2f".format(after.easeFactor)} " +
                "interval=${after.intervalDays}d requeue=$comesBackThisSession"
        }
        return session.copy(
            queue = if (comesBackThisSession) session.queue + card else session.queue,
            progressById = session.progressById + (card.id to after),
            lastAnswer = AnswerRecord(choice, correct, grade, responseMillis, before, after),
            correctCount = session.correctCount + if (correct) 1 else 0,
            wrongCount = session.wrongCount + if (correct) 0 else 1,
        )
    }

    /** Moves past the answered card. Ignored until the current card has been answered. */
    fun advance(session: StudySession, nowMillis: Long): StudySession =
        if (!session.isAwaitingAdvance) session
        else session.copy(queue = session.queue.drop(1), lastAnswer = null, shownAtMillis = nowMillis)

    /** Removes every queued copy of [cardId] (suspended / removed by the user) and stores its new progress. */
    fun withoutCard(session: StudySession, cardId: String, progress: CardProgress, nowMillis: Long): StudySession =
        session.copy(
            queue = session.queue.filterNot { it.id == cardId },
            progressById = session.progressById + (cardId to progress),
            lastAnswer = null,
            shownAtMillis = nowMillis,
        )
}

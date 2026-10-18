package dev.yuji.quiz.domain

import dev.yuji.quiz.debug.InMemoryStudyLog
import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.NoopStudyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyEngineTest {
    private val cfg = SchedulerConfig()
    private val card = Card("article:Tisch", GameMode.ARTICLE, "Tisch", null, listOf("der", "die", "das"), "der", "der Tisch", false)
    private fun session() = StudyEngine.start(
        GameMode.ARTICLE, StudyQueue(listOf(card), QueueStats(0, 0, 1)), emptyMap(), nowMillis = 0, choiceSeed = 1,
    )
    private fun answer(s: StudySession, choice: String, at: Long = 3_000, log: dev.yuji.quiz.debug.StudyLog = NoopStudyLog) =
        StudyEngine.answer(s, choice, at, cfg, GraderConfig(), log)

    @Test fun correctAnswerScoresAndSchedulesLearning() {
        val s = answer(session(), "der")
        assertEquals(1, s.correctCount)
        assertEquals(CardPhase.LEARNING, s.progressById.getValue(card.id).phase)
        assertTrue(s.isAwaitingAdvance)
    }

    @Test fun learningCardIsRequeuedInTheSameSession() {
        val s = answer(session(), "der")
        assertEquals(2, s.queue.size)               // current + requeued copy
        val after = StudyEngine.advance(s, 4_000)
        assertEquals(1, after.queue.size)
        assertFalse(after.isFinished)
    }

    @Test fun wrongAnswerCountsAndIsAgain() {
        val s = answer(session(), "die")
        assertEquals(1, s.wrongCount)
        assertEquals(ReviewGrade.AGAIN, s.lastAnswer!!.grade)
    }

    @Test fun duplicateOrInvalidAnswersAreIgnored() {
        val s = answer(session(), "der")
        assertEquals(s, answer(s, "die"))
        assertEquals(session(), answer(session(), "banana"))
    }

    @Test fun advanceIsIgnoredBeforeAnswering() {
        val s = session()
        assertEquals(s, StudyEngine.advance(s, 1))
    }

    @Test fun matureCardLeavesTheSessionAfterGoodAnswer() {
        val mature = CardProgress(card.id, phase = CardPhase.REVIEW, intervalDays = 5, dueAtMillis = 0, firstSeenAtMillis = 1)
        val s0 = StudyEngine.start(GameMode.ARTICLE, StudyQueue(listOf(card), QueueStats(0, 1, 0)), mapOf(card.id to mature), 0, 1)
        val s = StudyEngine.advance(answer(s0, "der", at = 5_000), 6_000)
        assertTrue(s.isFinished)
        assertTrue(s.progressById.getValue(card.id).intervalDays > 5)
    }

    @Test fun shuffledChoicesAreStableForSameState() {
        val shuffled = card.copy(shuffleChoices = true)
        val s = session().copy(queue = listOf(shuffled))
        assertEquals(StudyEngine.choicesFor(s), StudyEngine.choicesFor(s))
        assertEquals(shuffled.choices.toSet(), StudyEngine.choicesFor(s).toSet())
    }

    @Test fun answeringWritesATraceWhenChannelIsEnabled() {
        val log = InMemoryStudyLog(setOf(LogChannel.SESSION))
        answer(session(), "der", log = log)
        assertEquals(1, log.lines.size)
        assertTrue(log.lines.single().contains("article:Tisch"))
    }
}

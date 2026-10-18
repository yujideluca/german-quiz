package dev.yuji.quiz

import dev.yuji.quiz.data.InMemoryTrainingStore
import dev.yuji.quiz.data.ProgressStore
import dev.yuji.quiz.data.TrainingStore
import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.StudyLog
import dev.yuji.quiz.debug.debug
import dev.yuji.quiz.domain.Card
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DailyProgress
import dev.yuji.quiz.domain.clampDailyGoal
import dev.yuji.quiz.domain.epochDayOf
import dev.yuji.quiz.domain.answeredOn
import dev.yuji.quiz.domain.ActivityCalendar
import dev.yuji.quiz.domain.buildCalendar
import dev.yuji.quiz.domain.plusAnswer
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.CardStatus
import dev.yuji.quiz.domain.GraderConfig
import dev.yuji.quiz.domain.ManagedCard
import dev.yuji.quiz.domain.ManagedCounts
import dev.yuji.quiz.domain.SuspensionDuration
import dev.yuji.quiz.domain.SpacedRepetitionScheduler
import dev.yuji.quiz.domain.restored
import dev.yuji.quiz.domain.searchManagedCards
import dev.yuji.quiz.domain.statusAt
import dev.yuji.quiz.domain.withSuspension
import dev.yuji.quiz.domain.QueueLimits
import dev.yuji.quiz.domain.QueueStats
import dev.yuji.quiz.domain.SchedulerConfig
import dev.yuji.quiz.domain.StudyEngine
import dev.yuji.quiz.domain.StudyPipeline
import dev.yuji.quiz.domain.StudySession
import java.time.ZoneId

data class ModeOverview(val mode: GameMode, val totalCards: Int, val stats: QueueStats)

/**
 * Application service: wires deck, progress store, clock and configuration to the pure
 * domain functions. Contains no Android code, so it is fully unit-testable.
 */
class StudyController(
    private val deck: Map<GameMode, List<Card>>,
    private val store: ProgressStore,
    private val clock: Clock,
    private val log: StudyLog,
    private val schedulerConfig: SchedulerConfig = SchedulerConfig(),
    private val graderConfig: GraderConfig = GraderConfig(),
    private val limits: QueueLimits = QueueLimits(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val training: TrainingStore = InMemoryTrainingStore(),
) {
    private var progressById = store.loadAll()
    private val cardsById: Map<String, Card> = deck.values.flatten().associateBy { it.id }

    init {
        log.debug(LogChannel.CONTENT) {
            "deck: " + deck.entries.joinToString { "${it.key}=${it.value.size}" } +
                " | stored progress: ${progressById.size} cards"
        }
    }

    fun overview(): List<ModeOverview> = GameMode.entries.map { mode ->
        val cards = deck[mode].orEmpty()
        ModeOverview(mode, cards.size, queueFor(mode).stats)
    }

    fun startSession(mode: GameMode): StudySession {
        val queue = queueFor(mode)
        log.debug(LogChannel.SESSION) { "start $mode with ${queue.cards.size} cards" }
        return StudyEngine.start(mode, queue, progressById, clock.nowMillis(), choiceSeed = clock.nowMillis())
    }

    fun choices(session: StudySession) = StudyEngine.choicesFor(session)

    fun answer(session: StudySession, choice: String): StudySession {
        val updated = StudyEngine.answer(session, choice, clock.nowMillis(), schedulerConfig, graderConfig, log)
        val card = session.currentCard
        if (card != null && updated !== session) {
            val progress = updated.progressById.getValue(card.id)
            store.save(progress)
            progressById = progressById + (card.id to progress)
            training.history = training.history.plusAnswer(today())
        }
        return updated
    }

    /** Answers given today across all quizzes against the player's daily goal. */
    fun dailyProgress() = DailyProgress(training.history.answeredOn(today()), training.dailyGoal)

    fun setDailyGoal(goal: Int) {
        training.dailyGoal = clampDailyGoal(goal)
        log.debug(LogChannel.SESSION) { "daily goal set to ${training.dailyGoal}" }
    }

    /** Streak calendar for [month], coloured by how many questions were answered each day. */
    fun calendar(month: java.time.YearMonth): ActivityCalendar =
        buildCalendar(training.history, month, java.time.LocalDate.ofEpochDay(today()))

    fun currentMonth(): java.time.YearMonth = java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(today()))

    private fun today() = epochDayOf(clock.nowMillis(), zone)

    fun advance(session: StudySession): StudySession = StudyEngine.advance(session, clock.nowMillis())

    /** Suspends or removes the card currently shown and drops it from the running session. */
    fun suspendCurrentCard(session: StudySession, duration: SuspensionDuration): StudySession {
        val card = session.currentCard ?: return session
        val base = progressById[card.id] ?: SpacedRepetitionScheduler.freshProgress(card.id, schedulerConfig)
        val now = clock.nowMillis()
        val updated = base.withSuspension(duration, now)
        persist(updated)
        log.debug(LogChannel.SESSION) { "${card.id}: $duration (until=${updated.suspendedUntilMillis}, removed=${updated.removed})" }
        return StudyEngine.withoutCard(session, card.id, updated, now)
    }

    /** Suspended (still active) and removed cards, optionally filtered by a search query. */
    fun managedCards(query: String = ""): List<ManagedCard> {
        val now = clock.nowMillis()
        val all = progressById.values.mapNotNull { p ->
            val status = p.statusAt(now) ?: return@mapNotNull null
            cardsById[p.cardId]?.let { ManagedCard(it, status, p.suspendedUntilMillis) }
        }.sortedWith(compareBy({ it.status }, { it.card.prompt }))
        return searchManagedCards(all, query)
    }

    fun managedCounts(): ManagedCounts {
        val m = managedCards()
        return ManagedCounts(m.count { it.status == CardStatus.SUSPENDED }, m.count { it.status == CardStatus.REMOVED })
    }

    fun restoreCard(cardId: String) {
        val p = progressById[cardId] ?: return
        persist(p.restored())
        log.debug(LogChannel.SESSION) { "$cardId restored to the pool" }
    }

    fun restoreAll(status: CardStatus) {
        managedCards().filter { it.status == status }.forEach { restoreCard(it.card.id) }
    }

    private fun persist(progress: dev.yuji.quiz.domain.CardProgress) {
        store.save(progress)
        progressById = progressById + (progress.cardId to progress)
    }

    fun resetAllProgress() {
        store.clear()
        progressById = emptyMap()
        log.debug(LogChannel.STORAGE) { "all progress cleared" }
    }

    private fun queueFor(mode: GameMode) = StudyPipeline.buildQueue(
        modeCards = deck[mode].orEmpty(),
        progressById = progressById,
        nowMillis = clock.nowMillis(),
        limits = limits,
        config = schedulerConfig,
        log = log,
    )
}

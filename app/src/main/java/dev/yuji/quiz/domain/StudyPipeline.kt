package dev.yuji.quiz.domain

import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.StudyLog
import dev.yuji.quiz.debug.debug
import java.time.ZoneId

data class CardWithProgress(val card: Card, val progress: CardProgress)

data class QueueStats(val learningDue: Int, val reviewsDue: Int, val newAvailable: Int, val newBudgetLeft: Int)

data class StudyQueue(val cards: List<Card>, val stats: QueueStats)

/**
 * Builds the list of cards to study as a chain of small, individually testable steps:
 *
 *   attachProgress -> selectLearningDue -> selectReviewsDue -> selectNewCards -> interleave
 */
object StudyPipeline {

    /** Step 1: pair every card with its stored progress (fresh progress for unseen cards). */
    fun attachProgress(
        cards: List<Card>,
        progressById: Map<String, CardProgress>,
        config: SchedulerConfig,
    ): List<CardWithProgress> = cards.map {
        CardWithProgress(it, progressById[it.id] ?: SpacedRepetitionScheduler.freshProgress(it.id, config))
    }

    /** Step 1b: drop cards the user suspended (until it expires) or removed. */
    fun excludeUnavailable(items: List<CardWithProgress>, nowMillis: Long) =
        items.filter { it.progress.isAvailableAt(nowMillis) }

    /** Step 2: cards in (re)learning that are due now or within the learn-ahead window. */
    fun selectLearningDue(items: List<CardWithProgress>, nowMillis: Long, learnAheadMinutes: Int) =
        items.filter {
            (it.progress.phase == CardPhase.LEARNING || it.progress.phase == CardPhase.RELEARNING) &&
                it.progress.dueAtMillis <= nowMillis + learnAheadMinutes * MINUTE_MILLIS
        }.sortedBy { it.progress.dueAtMillis }

    /** Step 3: graduated cards that are due, most overdue first, capped. */
    fun selectReviewsDue(items: List<CardWithProgress>, nowMillis: Long, limit: Int) =
        items.filter { it.progress.phase == CardPhase.REVIEW && it.progress.dueAtMillis <= nowMillis }
            .sortedBy { it.progress.dueAtMillis }
            .take(limit)

    /** How many new cards were first seen since [dayStartMillis] (counts against the daily budget). */
    fun countIntroducedSince(items: List<CardWithProgress>, dayStartMillis: Long) =
        items.count { (it.progress.firstSeenAtMillis ?: Long.MIN_VALUE) >= dayStartMillis }

    /** Step 4: unseen cards in deck order, capped by the remaining daily budget. */
    fun selectNewCards(items: List<CardWithProgress>, budget: Int) =
        items.filter { it.progress.phase == CardPhase.NEW }.take(budget.coerceAtLeast(0))

    /** Step 5: keep the main list's order and slot one extra item after every [every] main items. */
    fun <T> interleave(main: List<T>, extra: List<T>, every: Int): List<T> {
        if (extra.isEmpty()) return main
        if (main.isEmpty()) return extra
        val out = ArrayList<T>(main.size + extra.size)
        val extras = extra.iterator()
        main.forEachIndexed { i, item ->
            out += item
            if ((i + 1) % every == 0 && extras.hasNext()) out += extras.next()
        }
        extras.forEachRemaining { out += it }
        return out
    }

    /** Full pipeline for one mode's cards. Learning cards always come first (they are time-critical). */
    fun buildQueue(
        modeCards: List<Card>,
        progressById: Map<String, CardProgress>,
        nowMillis: Long,
        zone: ZoneId,
        limits: QueueLimits,
        config: SchedulerConfig,
        log: StudyLog,
    ): StudyQueue {
        val all = attachProgress(modeCards, progressById, config)
        val items = excludeUnavailable(all, nowMillis)
        val learning = selectLearningDue(items, nowMillis, config.learnAheadMinutes)
        val reviews = selectReviewsDue(items, nowMillis, limits.maxReviewsPerSession)
        val introduced = countIntroducedSince(all, startOfDayMillis(nowMillis, zone))
        val budget = limits.maxNewCardsPerDay - introduced
        val newCards = selectNewCards(items, budget)
        val ordered = learning + interleave(reviews, newCards, limits.reviewsPerNewCard)
        val stats = QueueStats(learning.size, reviews.size, newCards.size, budget.coerceAtLeast(0))
        log.debug(LogChannel.PIPELINE) {
            "queue built: ${ordered.size} cards (learning=${learning.size}, reviews=${reviews.size}, " +
                "new=${newCards.size}, introducedToday=$introduced, budgetLeft=${stats.newBudgetLeft}, " +
                "hidden=${all.size - items.size})"
        }
        return StudyQueue(ordered.map { it.card }, stats)
    }
}

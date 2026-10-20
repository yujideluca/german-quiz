package dev.yuji.quiz

import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.content.NounEntry
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.MINUTE_MILLIS
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ SP-1 SP-2 SP-3 SP-6 QU-1 SE-3
 *
 * Property-based test: thousands of random study histories (random pauses, wrong/right answers, fast and
 * slow thinking, leaving and re-opening the quiz) are replayed through the real [StudyController]. After every
 * step the cards on offer are compared with an independent oracle that knows only the requirements:
 *
 *  - a card answered wrong is due exactly 10 minutes later;
 *  - a card answered right that has never been answered wrong is due exactly 3 days later (first time) or
 *    strictly later than its previous gap (every time after);
 *  - nothing is offered before its due time, everything due is offered.
 *
 * Seeds are fixed, so a failure is reproducible: the message names the seed and step.
 */
class SchedulingPropertyTest {
    private class Oracle {
        /** Exact due time the requirements dictate, or null when only a lower bound is known. */
        var exactDue: Long? = null
        var lowerBound: Long = 0
        var everAnswered = false
        var everWrong = false
        var lastGapMillis = 0L
        var everCorrect = false
        var lastWasCorrect = false
    }

    private val articles = ('a'..'h').map { NounEntry("der", "Wort$it", "Worte$it") }
    private val deck = CardFactory.buildDeck(articles, emptyList())

    private fun runOne(seed: Int, steps: Int) {
        var now = 1_000 * DAY_MILLIS
        val rnd = Random(seed)
        val store = InMemoryProgressStore()
        val controller = StudyController(deck, store, Clock { now }, NoopStudyLog)
        val oracle = deck.getValue(GameMode.ARTICLE).associate { it.id to Oracle() }

        repeat(steps) { step ->
            val where = "seed=$seed step=$step now=${(now - 1_000 * DAY_MILLIS) / MINUTE_MILLIS}min"

            // time passes: sometimes exactly onto a boundary of the oracle, otherwise a random pause
            val boundaries = oracle.values.mapNotNull { it.exactDue }.filter { it >= now }
            now += when {
                boundaries.isNotEmpty() && rnd.nextInt(10) < 3 ->
                    boundaries.random(rnd) - now - rnd.nextInt(2)          // exactly at, or 1 ms before, a due time
                else -> listOf(5_000L, MINUTE_MILLIS, 9 * MINUTE_MILLIS, 10 * MINUTE_MILLIS, 3 * 3_600_000L,
                    DAY_MILLIS, 3 * DAY_MILLIS, 10 * DAY_MILLIS, 40 * DAY_MILLIS).random(rnd)
            }

            // the player opens the quiz: what is on offer must match the oracle exactly
            var session = controller.startSession(GameMode.ARTICLE)
            val offered = session.queue.map { it.id }
            assertEquals("$where: no card twice in a fresh queue", offered.size, offered.toSet().size)
            for ((id, o) in oracle) {
                val due = o.exactDue
                if (!o.everAnswered) assertTrue("$where: unseen $id must be offered", id in offered)
                if (id in offered && o.everAnswered)
                    assertTrue("$where: $id offered at $now before its earliest time ${o.lowerBound}", now >= o.lowerBound)
                if (due != null && now >= due)
                    assertTrue("$where: $id was due at $due but is not offered", id in offered)
                if (due != null && now < due)
                    assertTrue("$where: $id offered before its exact due time $due", id !in offered)
            }

            // the player answers the first card, sometimes right, sometimes wrong, sometimes slowly
            val card = session.currentCard ?: return@repeat
            val o = oracle.getValue(card.id)
            val correct = rnd.nextInt(100) < 70
            now += listOf(800L, 2_000L, 6_000L, 12_000L, 30_000L).random(rnd)
            session = controller.answer(session, if (correct) card.correctChoice else card.choices.first { it != card.correctChoice })
            val progress = store.loadAll().getValue(card.id)
            val gap = progress.dueAtMillis - now

            if (!correct) {
                assertEquals("$where: wrong answer on ${card.id} is due after exactly 10 min", 10 * MINUTE_MILLIS, gap)
                o.exactDue = progress.dueAtMillis; o.lowerBound = progress.dueAtMillis; o.everWrong = true
            } else if (!o.everCorrect) {
                // a new card answered right goes to 3 days, whether or not it was missed once before
                assertEquals("$where: a new card answered correctly is due after exactly 3 days", 3 * DAY_MILLIS, gap)
                o.exactDue = progress.dueAtMillis; o.lowerBound = progress.dueAtMillis
            } else if (o.lastWasCorrect) {
                assertTrue("$where: a correct answer after a correct answer lengthens the gap ($gap vs ${o.lastGapMillis})", gap > o.lastGapMillis)
                o.exactDue = null; o.lowerBound = progress.dueAtMillis
            } else {
                assertTrue("$where: after a lapse the card waits at least a day (gap=${gap / MINUTE_MILLIS}min, phase=${progress.phase}, answered ${(now - progress.lastReviewedAtMillis!!)}ms ago, correct=$correct)", gap >= DAY_MILLIS)
                o.exactDue = null; o.lowerBound = progress.dueAtMillis
            }
            if (correct) o.everCorrect = true
            o.everAnswered = true
            o.lastGapMillis = gap
            o.lastWasCorrect = correct
        }
    }

    @Test fun twoThousandRandomHistoriesFollowTheSpacingRequirements() {
        for (seed in 1..2_000) runOne(seed, steps = 90)
    }
}

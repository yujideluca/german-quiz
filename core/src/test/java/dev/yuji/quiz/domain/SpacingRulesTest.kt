package dev.yuji.quiz.domain

// REQ SP-1 SP-3 SE-3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The spacing promise, checked for the cards of every mode: first answer -> 3 days, then ever longer. */
class SpacingRulesTest {
    private val cfg = SchedulerConfig()
    private val now = 1_000 * DAY_MILLIS

    private fun card(mode: GameMode) =
        Card("${mode.name}:x", mode, "x", null, listOf("a", "b", "c"), "a", "a x", false)

    @Test fun everyModeStartsWithNewCardsAndShowsThemAgainOnlyAfterThreeDays() {
        for (mode in GameMode.entries) {
            val fresh = SpacedRepetitionScheduler.freshProgress(card(mode).id, cfg)
            assertEquals(mode.name, CardPhase.NEW, fresh.phase)
            val p = SpacedRepetitionScheduler.scheduleNext(fresh, ReviewGrade.GOOD, now, cfg)
            assertEquals(mode.name, now + 3 * DAY_MILLIS, p.dueAtMillis)
            val queueSoon = StudyPipeline.buildQueue(listOf(card(mode)), mapOf(p.cardId to p), now + 3 * DAY_MILLIS - 1, QueueLimits(), cfg,
                dev.yuji.quiz.debug.NoopStudyLog)
            assertTrue("$mode must not reappear before day 3", queueSoon.cards.isEmpty())
            val queueDue = StudyPipeline.buildQueue(listOf(card(mode)), mapOf(p.cardId to p), now + 3 * DAY_MILLIS, QueueLimits(), cfg,
                dev.yuji.quiz.debug.NoopStudyLog)
            assertEquals(1, queueDue.cards.size)
        }
    }

    @Test fun eachCorrectAnswerPushesTheNextShowingFurtherOut() {
        var p = SpacedRepetitionScheduler.freshProgress("c", cfg)
        var t = now
        var last = 0
        repeat(8) {
            p = SpacedRepetitionScheduler.scheduleNext(p, ReviewGrade.GOOD, t, cfg)
            assertTrue("interval ${p.intervalDays} must exceed $last", p.intervalDays > last)
            last = p.intervalDays
            t = p.dueAtMillis
        }
        assertEquals(3, SpacedRepetitionScheduler.scheduleNext(SpacedRepetitionScheduler.freshProgress("c", cfg), ReviewGrade.GOOD, now, cfg).intervalDays)
    }
}

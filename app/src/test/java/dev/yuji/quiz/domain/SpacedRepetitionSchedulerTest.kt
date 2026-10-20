package dev.yuji.quiz.domain

// REQ SP-1 SP-2 SP-3 SP-4 SP-5 SP-6 GR-1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionSchedulerTest {
    private val cfg = SchedulerConfig()
    private val now = 1_000_000_000L
    private fun fresh() = SpacedRepetitionScheduler.freshProgress("c", cfg)
    private fun next(p: CardProgress, g: ReviewGrade, at: Long = now) =
        SpacedRepetitionScheduler.scheduleNext(p, g, at, cfg)

    @Test fun newCardAgainStartsLearningAtStepZero() {
        val p = next(fresh(), ReviewGrade.AGAIN)
        assertEquals(CardPhase.LEARNING, p.phase)
        assertEquals(0, p.learningStep)
        assertEquals(now + 10 * MINUTE_MILLIS, p.dueAtMillis)
    }

    @Test fun firstGoodGraduatesToThreeDays() {
        val p = next(fresh(), ReviewGrade.GOOD)
        assertEquals(CardPhase.REVIEW, p.phase)
        assertEquals(3, p.intervalDays)
        assertEquals(now + 3 * DAY_MILLIS, p.dueAtMillis)
    }

    @Test fun againOnNewRepeatsAfterTenMinutesThenGoodGraduates() {
        val a = next(fresh(), ReviewGrade.AGAIN)
        assertEquals(CardPhase.LEARNING, a.phase)
        assertEquals(now + 10 * MINUTE_MILLIS, a.dueAtMillis)
        val b = next(a, ReviewGrade.GOOD)
        assertEquals(CardPhase.REVIEW, b.phase)
        assertEquals(3, b.intervalDays)
    }

    @Test fun easyOnNewGraduatesToEasyInterval() {
        val p = next(fresh(), ReviewGrade.EASY)
        assertEquals(CardPhase.REVIEW, p.phase)
        assertEquals(cfg.easyIntervalDays, p.intervalDays)
    }

    @Test fun hardKeepsLearningStep() {
        val a = next(fresh(), ReviewGrade.AGAIN)
        assertEquals(CardPhase.LEARNING, next(a, ReviewGrade.HARD).phase)
    }

    private fun review(interval: Int, ease: Double = 2.5) =
        fresh().copy(phase = CardPhase.REVIEW, intervalDays = interval, easeFactor = ease, firstSeenAtMillis = 1)

    @Test fun reviewGoodMultipliesByEase() {
        val p = next(review(10), ReviewGrade.GOOD)
        assertEquals(25, p.intervalDays)
        assertEquals(2.5, p.easeFactor, 1e-9)
    }

    @Test fun reviewHardShrinksEaseAndGrowsSlowly() {
        val p = next(review(10), ReviewGrade.HARD)
        assertEquals(12, p.intervalDays)
        assertEquals(2.35, p.easeFactor, 1e-9)
    }

    @Test fun reviewEasyGrowsMostAndRaisesEase() {
        val p = next(review(10), ReviewGrade.EASY)
        assertEquals(33, p.intervalDays)          // 10 * 2.5 * 1.3
        assertEquals(2.65, p.easeFactor, 1e-9)
    }

    @Test fun lapseGoesToRelearningAndLowersEase() {
        val p = next(review(30), ReviewGrade.AGAIN)
        assertEquals(CardPhase.RELEARNING, p.phase)
        assertEquals(1, p.lapses)
        assertEquals(2.3, p.easeFactor, 1e-9)
        assertEquals(1, p.intervalDays)
        assertEquals(now + 10 * MINUTE_MILLIS, p.dueAtMillis)
    }

    @Test fun relearningGoodReturnsToReview() {
        val lapsed = next(review(30), ReviewGrade.AGAIN)
        val back = next(lapsed, ReviewGrade.GOOD)
        assertEquals(CardPhase.REVIEW, back.phase)
        assertEquals(now + back.intervalDays * DAY_MILLIS, back.dueAtMillis)
    }

    @Test fun easeNeverDropsBelowMinimum() {
        var p = review(5, ease = 1.35)
        repeat(5) { p = next(p.copy(phase = CardPhase.REVIEW), ReviewGrade.AGAIN) }
        assertTrue(p.easeFactor >= cfg.minimumEase)
    }

    @Test fun intervalIsCapped() {
        val p = next(review(30_000, ease = 3.0), ReviewGrade.EASY)
        assertEquals(cfg.maximumIntervalDays, p.intervalDays)
    }

    @Test fun firstSeenIsSetOnceAndRepetitionsCount() {
        val a = next(fresh(), ReviewGrade.GOOD, at = 5)
        val b = next(a, ReviewGrade.GOOD, at = 99)
        assertEquals(5L, b.firstSeenAtMillis)
        assertEquals(2, b.repetitions)
    }

    @Test fun correctAnswersLengthenTheGapEvenAtTheLowestEase() {   // REQ SP-3 (low-ease boundary)
        var p = review(1, ease = cfg.minimumEase)
        var last = p.intervalDays
        repeat(10) {
            p = next(p, ReviewGrade.GOOD, p.dueAtMillis)
            assertTrue("interval ${p.intervalDays} must exceed $last", p.intervalDays > last)
            last = p.intervalDays
        }
    }

    @Test fun moreLearningStepsAreWalkedInOrderBeforeGraduating() {   // the step logic is general, not tied to one step
        val two = SchedulerConfig(learningStepsMinutes = listOf(1, 10))
        val a = SpacedRepetitionScheduler.scheduleNext(fresh(), ReviewGrade.GOOD, now, two)
        assertEquals(CardPhase.LEARNING, a.phase)
        assertEquals(now + 10 * MINUTE_MILLIS, a.dueAtMillis)
        val b = SpacedRepetitionScheduler.scheduleNext(a, ReviewGrade.GOOD, now, two)
        assertEquals(CardPhase.REVIEW, b.phase)
    }
}

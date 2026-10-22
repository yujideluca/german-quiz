package dev.yuji.quiz.domain

import dev.yuji.quiz.debug.NoopStudyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ SP-1 SP-2 QU-1 DG-1 UP-1: boundary values (just below, on, just above each limit). */
class BoundaryValueTest {
    private val now = 10 * DAY_MILLIS
    private val cfg = SchedulerConfig()
    private fun card(id: String) = Card(id, GameMode.ARTICLE, id, null, listOf("der", "die"), "der", "", false)
    private fun offered(p: CardProgress) =
        StudyPipeline.buildQueue(listOf(card(p.cardId)), mapOf(p.cardId to p), now, QueueLimits(), cfg, NoopStudyLog).cards.isNotEmpty()

    private fun p(phase: CardPhase, due: Long) = CardProgress("c", phase = phase, dueAtMillis = due, intervalDays = 3, firstSeenAtMillis = 1)

    @Test fun aReviewIsOfferedFromTheExactMillisecondItIsDue() {
        assertFalse(offered(p(CardPhase.REVIEW, now + 1)))
        assertTrue(offered(p(CardPhase.REVIEW, now)))
        assertTrue(offered(p(CardPhase.REVIEW, now - 1)))
    }

    @Test fun aLearningCardIsOfferedFromTheExactMillisecondItIsDue() {
        assertFalse(offered(p(CardPhase.LEARNING, now + 1)))
        assertTrue(offered(p(CardPhase.LEARNING, now)))
        assertFalse(offered(p(CardPhase.RELEARNING, now + 9 * MINUTE_MILLIS)))
    }

    @Test fun theDueTimeIsExactlyThreeDaysAndExactlyTenMinutes() {
        val fresh = SpacedRepetitionScheduler.freshProgress("c")
        assertEquals(now + 3 * DAY_MILLIS, SpacedRepetitionScheduler.scheduleNext(fresh, ReviewGrade.GOOD, now).dueAtMillis)
        assertEquals(now + 10 * MINUTE_MILLIS, SpacedRepetitionScheduler.scheduleNext(fresh, ReviewGrade.AGAIN, now).dueAtMillis)
    }

    @Test fun graderThresholds() {
        val g = { ms: Long -> AnswerGrader.grade(true, ms, CardPhase.REVIEW) }
        assertEquals(ReviewGrade.EASY, g(2_499)); assertEquals(ReviewGrade.EASY, g(2_500)); assertEquals(ReviewGrade.GOOD, g(2_501))
        assertEquals(ReviewGrade.GOOD, g(9_999)); assertEquals(ReviewGrade.HARD, g(10_000))
    }

    @Test fun dailyGoalSnapsToTenToHundred() {
        assertEquals(10, clampDailyGoal(-5)); assertEquals(10, clampDailyGoal(9)); assertEquals(10, clampDailyGoal(10))
        assertEquals(10, clampDailyGoal(14)); assertEquals(20, clampDailyGoal(15)); assertEquals(100, clampDailyGoal(100)); assertEquals(100, clampDailyGoal(101))
        assertEquals(100, clampDailyGoal(10_000))
    }

    @Test fun updateCheckGapIsInclusive() {
        val gap = UPDATE_CHECK_MIN_GAP_MILLIS
        assertFalse(shouldCheckForUpdate(1_000 + gap - 1, 1_000)); assertTrue(shouldCheckForUpdate(1_000 + gap, 1_000))
        assertTrue(shouldCheckForUpdate(5, null))
    }

    @Test fun anOversizedIntervalIsCappedExactly() {
        val big = CardProgress("c", phase = CardPhase.REVIEW, intervalDays = cfg.maximumIntervalDays, easeFactor = 2.5, firstSeenAtMillis = 1)
        assertEquals(cfg.maximumIntervalDays, SpacedRepetitionScheduler.scheduleNext(big, ReviewGrade.EASY, now).intervalDays)
    }
}

package dev.yuji.quiz.domain

import dev.yuji.quiz.debug.NoopStudyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPipelineTest {
    private val cfg = SchedulerConfig()
    private val now = 10 * DAY_MILLIS + 12 * 60 * MINUTE_MILLIS // noon, day 10
    private fun card(n: Int) = Card("c$n", GameMode.ARTICLE, "w$n", null, listOf("der", "die"), "der", "", false)
    private val cards = (1..30).map(::card)

    private fun progress(id: String, phase: CardPhase, due: Long, firstSeen: Long? = 1L) =
        CardProgress(id, phase = phase, dueAtMillis = due, intervalDays = 3, firstSeenAtMillis = firstSeen)

    private fun build(progress: Map<String, CardProgress>, limits: QueueLimits = QueueLimits()) =
        StudyPipeline.buildQueue(cards, progress, now, limits, cfg, NoopStudyLog)

    @Test fun unseenDeckOffersEveryNewCardWithNoDailyCap() {
        val q = build(emptyMap())
        assertEquals(30, q.cards.size)
        assertEquals(30, q.stats.newAvailable)
    }

    @Test fun cardsIntroducedTodayDoNotReduceTheNewCards() {
        val today = now - MINUTE_MILLIS
        val seen = (1..4).associate { "c$it" to progress("c$it", CardPhase.REVIEW, now + DAY_MILLIS, today) }
        assertEquals(26, build(seen).stats.newAvailable)
    }

    @Test fun dueReviewsAreOverdueFirstAndFutureOnesExcluded() {
        val p = mapOf(
            "c1" to progress("c1", CardPhase.REVIEW, now - DAY_MILLIS),
            "c2" to progress("c2", CardPhase.REVIEW, now - 5 * DAY_MILLIS),
            "c3" to progress("c3", CardPhase.REVIEW, now + DAY_MILLIS),
        )
        val q = build(p)
        assertEquals(listOf("c2", "c1"), q.cards.take(2).map { it.id })
    }

    @Test fun learningCardsComeFirstOnceTheirWaitIsOver() {
        val p = mapOf(
            "c1" to progress("c1", CardPhase.REVIEW, now - DAY_MILLIS),
            "c2" to progress("c2", CardPhase.LEARNING, now - 1),                    // wait is over
            "c3" to progress("c3", CardPhase.LEARNING, now + 5 * MINUTE_MILLIS),    // still waiting: not offered
        )
        val q = build(p)
        assertEquals(listOf("c2", "c1"), q.cards.take(2).map { it.id })
        assertTrue("c3" !in q.cards.map { it.id })
    }

    @Test fun interleaveSlotsExtrasAfterEveryNMain() {
        assertEquals(listOf(1, 2, 3, 100, 4, 5, 6, 101), StudyPipeline.interleave(listOf(1, 2, 3, 4, 5, 6), listOf(100, 101), 3))
        assertEquals(listOf(100, 101), StudyPipeline.interleave(emptyList(), listOf(100, 101), 3))
        assertTrue(StudyPipeline.interleave(listOf(1), emptyList<Int>(), 3) == listOf(1))
    }
}

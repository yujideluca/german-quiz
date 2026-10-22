package dev.yuji.quiz.domain

// REQ MG-1 QU-1

import dev.yuji.quiz.debug.NoopStudyLog
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAvailabilityTest {
    private val now = 50 * DAY_MILLIS
    private fun card(id: String, prompt: String = id, context: String? = null) =
        Card(id, GameMode.ARTICLE, prompt, context, listOf("der", "die"), "der", "expl $id", false)

    @Test fun weekAndMonthSuspensionsExpire() {
        val week = CardProgress("c").withSuspension(SuspensionDuration.ONE_WEEK, now)
        assertFalse(week.isAvailableAt(now + 6 * DAY_MILLIS))
        assertTrue(week.isAvailableAt(now + 7 * DAY_MILLIS))
        val month = CardProgress("c").withSuspension(SuspensionDuration.ONE_MONTH, now)
        assertFalse(month.isAvailableAt(now + 29 * DAY_MILLIS))
        assertTrue(month.isAvailableAt(now + 30 * DAY_MILLIS))
    }

    @Test fun permanentRemovalNeverExpires() {
        val p = CardProgress("c").withSuspension(SuspensionDuration.PERMANENT, now)
        assertEquals(CardStatus.REMOVED, p.statusAt(now + 10_000 * DAY_MILLIS))
        assertFalse(p.isAvailableAt(now + 10_000 * DAY_MILLIS))
    }

    @Test fun statusIsNullOnceSuspensionExpired() {
        val p = CardProgress("c").withSuspension(SuspensionDuration.ONE_WEEK, now)
        assertEquals(CardStatus.SUSPENDED, p.statusAt(now + DAY_MILLIS))
        assertNull(p.statusAt(now + 8 * DAY_MILLIS))
    }

    @Test fun restoreKeepsSchedulingState() {
        val studied = CardProgress("c", phase = CardPhase.REVIEW, intervalDays = 12, easeFactor = 2.2)
        val back = studied.withSuspension(SuspensionDuration.PERMANENT, now).restored()
        assertEquals(studied, back)
    }

    @Test fun pipelineSkipsSuspendedAndRemovedCards() {
        val cards = (1..3).map { card("c$it") }
        val progress = mapOf(
            "c1" to CardProgress("c1").withSuspension(SuspensionDuration.ONE_WEEK, now),
            "c2" to CardProgress("c2").withSuspension(SuspensionDuration.PERMANENT, now),
        )
        val q = StudyPipeline.buildQueue(cards, progress, now, QueueLimits(), SchedulerConfig(), NoopStudyLog)
        assertEquals(listOf("c3"), q.cards.map { it.id })
    }

    @Test fun searchIgnoresCaseAndUmlauts() {
        val items = listOf(
            ManagedCard(card("plural:Stuhl", "der Stuhl"), CardStatus.REMOVED, 0),
            ManagedCard(card("article:Tisch", "Tisch"), CardStatus.SUSPENDED, now),
        )
        assertEquals(listOf("plural:Stuhl"), searchManagedCards(items, "STÜH").map { it.card.id })
        assertEquals(2, searchManagedCards(items, "  ").size)
        assertTrue(searchManagedCards(items, "xyz").isEmpty())
    }
}

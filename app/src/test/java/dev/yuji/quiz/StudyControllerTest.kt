package dev.yuji.quiz

import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.content.NounEntry
import dev.yuji.quiz.content.PrepositionEntry
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.QueueLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyControllerTest {
    private var now = 100 * DAY_MILLIS
    private val clock = Clock { now }
    private val deck = CardFactory.buildDeck(
        listOf(NounEntry("der", "Tisch", "Tische"), NounEntry("die", "Lampe", "Lampen")),
        listOf(PrepositionEntry("V", "warten", "auf", "A", "Ich warte ___ den Bus.")),
    )
    private fun controller(store: InMemoryProgressStore) =
        StudyController(deck, store, clock, NoopStudyLog, limits = QueueLimits(maxNewCardsPerDay = 5))

    @Test fun overviewListsEveryModeWithNewCards() {
        val o = controller(InMemoryProgressStore()).overview().associateBy { it.mode }
        assertEquals(2, o.getValue(GameMode.ARTICLE).stats.newAvailable)
        assertEquals(1, o.getValue(GameMode.PREPOSITION).stats.newAvailable)
    }

    @Test fun answersArePersistedAndSurviveARestart() {
        val store = InMemoryProgressStore()
        val c1 = controller(store)
        var s = c1.startSession(GameMode.ARTICLE)
        s = c1.answer(s, s.currentCard!!.correctChoice)
        assertTrue(store.loadAll().containsKey(s.currentCard!!.id))

        val c2 = controller(store)                          // simulated app restart
        val articleStats = c2.overview().first { it.mode == GameMode.ARTICLE }.stats
        assertEquals(1, articleStats.learningDue)           // seen card is now in learning
        assertEquals(1, articleStats.newAvailable)
    }

    @Test fun resetClearsProgress() {
        val store = InMemoryProgressStore()
        val c = controller(store)
        var s = c.startSession(GameMode.PLURAL)
        c.answer(s, s.currentCard!!.correctChoice)
        c.resetAllProgress()
        assertTrue(store.loadAll().isEmpty())
        assertEquals(2, c.overview().first { it.mode == GameMode.PLURAL }.stats.newAvailable)
    }

    @Test fun nextDayBringsGraduatedCardsBackAsReviews() {
        val store = InMemoryProgressStore()
        val c = controller(store)
        var s = c.startSession(GameMode.PREPOSITION)
        // answer twice correctly: learning step 0 -> 1 -> graduated (interval 1 day)
        repeat(2) {
            s = c.answer(s, s.currentCard!!.correctChoice)
            s = c.advance(s)
        }
        now += 2 * DAY_MILLIS
        assertEquals(1, controller(store).overview().first { it.mode == GameMode.PREPOSITION }.stats.reviewsDue)
    }
}

class StudyControllerManagementTest {
    private var now = 100 * DAY_MILLIS
    private val deck = CardFactory.buildDeck(
        listOf(NounEntry("der", "Tisch", "Tische"), NounEntry("die", "Lampe", "Lampen"), NounEntry("das", "Buch", "Bücher")),
        emptyList(),
    )
    private fun controller(store: InMemoryProgressStore) =
        StudyController(deck, store, Clock { now }, NoopStudyLog)

    @Test fun suspendedCardLeavesSessionAndPoolAndSurvivesRestart() {
        val store = InMemoryProgressStore()
        val c = controller(store)
        var s = c.startSession(GameMode.ARTICLE)
        val victim = s.currentCard!!.id
        s = c.suspendCurrentCard(s, dev.yuji.quiz.domain.SuspensionDuration.ONE_WEEK)
        assertTrue(s.queue.none { it.id == victim })
        assertEquals(2, c.overview().first { it.mode == GameMode.ARTICLE }.stats.newAvailable)
        assertEquals(1, controller(store).managedCounts().suspended)   // persisted
    }

    @Test fun suspensionEndsAfterAWeek() {
        val store = InMemoryProgressStore()
        val c = controller(store)
        c.suspendCurrentCard(c.startSession(GameMode.ARTICLE), dev.yuji.quiz.domain.SuspensionDuration.ONE_WEEK)
        now += 8 * DAY_MILLIS
        assertEquals(3, controller(store).overview().first { it.mode == GameMode.ARTICLE }.stats.newAvailable)
    }

    @Test fun resetSuspendedLeavesRemovedCardsAlone() {
        val store = InMemoryProgressStore()
        val c = controller(store)
        var s = c.startSession(GameMode.ARTICLE)
        s = c.suspendCurrentCard(s, dev.yuji.quiz.domain.SuspensionDuration.ONE_MONTH)
        c.suspendCurrentCard(s, dev.yuji.quiz.domain.SuspensionDuration.PERMANENT)
        assertEquals(dev.yuji.quiz.domain.ManagedCounts(1, 1), c.managedCounts())
        c.restoreAll(dev.yuji.quiz.domain.CardStatus.SUSPENDED)
        assertEquals(dev.yuji.quiz.domain.ManagedCounts(0, 1), c.managedCounts())
        c.restoreAll(dev.yuji.quiz.domain.CardStatus.REMOVED)
        assertEquals(dev.yuji.quiz.domain.ManagedCounts(0, 0), c.managedCounts())
    }

    @Test fun singleCardCanBeFoundAndRestored() {
        val store = InMemoryProgressStore()
        val c = controller(store)
        val s = c.startSession(GameMode.ARTICLE)
        val prompt = s.currentCard!!.prompt
        c.suspendCurrentCard(s, dev.yuji.quiz.domain.SuspensionDuration.PERMANENT)
        val found = c.managedCards(prompt.lowercase()).single()
        c.restoreCard(found.card.id)
        assertTrue(c.managedCards().isEmpty())
    }
}

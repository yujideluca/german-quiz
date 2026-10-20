package dev.yuji.quiz

import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.content.NounEntry
import dev.yuji.quiz.content.PrepositionEntry
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.BackupParse
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.ProgressBackup
import dev.yuji.quiz.domain.decodeBackup
import dev.yuji.quiz.domain.encodeBackup
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
        StudyController(deck, store, clock, NoopStudyLog)

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
        assertEquals(0, articleStats.learningDue)
        assertEquals(0, articleStats.reviewsDue)            // seen card waits 3 days
        assertEquals(1, articleStats.newAvailable)
    }

    @Test fun exportedProgressRestoresOnAnotherPhone() {
        val oldPhone = controller(InMemoryProgressStore())
        var s = oldPhone.startSession(GameMode.ARTICLE)
        s = oldPhone.answer(s, s.currentCard!!.correctChoice)
        oldPhone.setDailyGoal(70)
        val file = encodeBackup(oldPhone.exportBackup("1.5", "DARK"))

        val newStore = InMemoryProgressStore()
        val newPhone = controller(newStore)
        newPhone.importBackup((decodeBackup(file) as BackupParse.Ok).backup)

        assertEquals(oldPhone.overview().map { it.stats }, newPhone.overview().map { it.stats })
        assertEquals(oldPhone.dailyProgress(), newPhone.dailyProgress())
        assertEquals(70, newPhone.dailyProgress().goal)
        assertEquals(1, newStore.loadAll().size)
    }

    @Test fun importReplacesExistingProgressAndIgnoresUnknownCards() {
        val store = InMemoryProgressStore()
        val c = controller(store)
        var s = c.startSession(GameMode.PLURAL)
        c.answer(s, s.currentCard!!.correctChoice)               // progress that the import must replace
        val stranger = dev.yuji.quiz.domain.CardProgress("no-such-card")
        c.importBackup(ProgressBackup(
            exportedAtMillis = now, appVersion = "1.5", progress = mapOf("no-such-card" to stranger),
            history = emptyMap(), dailyGoal = 50,
        ))
        assertTrue(store.loadAll().isEmpty())
        assertEquals(0, c.dailyProgress().answered)
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
        // one correct answer sends the card away for 3 days
        s = c.answer(s, s.currentCard!!.correctChoice)
        s = c.advance(s)
        now += 2 * DAY_MILLIS
        assertEquals("not due after 2 days", 0, controller(store).overview().first { it.mode == GameMode.PREPOSITION }.stats.reviewsDue)
        now += DAY_MILLIS
        assertEquals(1, controller(store).overview().first { it.mode == GameMode.PREPOSITION }.stats.reviewsDue)
    }
}

class DailyGoalTest {
    private var now = 100 * DAY_MILLIS + 3_600_000
    private val deck = CardFactory.buildDeck(
        listOf(NounEntry("der", "Tisch", "Tische"), NounEntry("die", "Lampe", "Lampen")),
        listOf(PrepositionEntry("V", "warten", "auf", "A", "Ich warte ___ den Bus.")),
    )
    private fun controller(training: dev.yuji.quiz.data.InMemoryTrainingStore) =
        StudyController(deck, InMemoryProgressStore(), Clock { now }, NoopStudyLog, zone = java.time.ZoneOffset.UTC, training = training)

    @Test fun answersInAnyQuizCountTowardTheSameDailyGoalAndNothingCapsTheQueue() {
        val c = controller(dev.yuji.quiz.data.InMemoryTrainingStore(dailyGoal = 10))
        var a = c.startSession(GameMode.ARTICLE)
        a = c.answer(a, a.currentCard!!.correctChoice)
        var p = c.startSession(GameMode.PREPOSITION)
        c.answer(p, p.currentCard!!.correctChoice)
        assertEquals(dev.yuji.quiz.domain.DailyProgress(2, 10), c.dailyProgress())
        assertEquals(1, c.overview().first { it.mode == GameMode.ARTICLE }.stats.let { it.newAvailable + it.learningDue } )   // the answered card now waits 3 days
    }

    @Test fun goalReachedKeepsStudyingPossible() {
        val c = controller(dev.yuji.quiz.data.InMemoryTrainingStore(dailyGoal = 3))
        var s = c.startSession(GameMode.ARTICLE)
        val missed = mutableSetOf<String>()
        repeat(12) {
            val card = s.currentCard
            if (card != null) {
                // miss each card once, wait out its 10 minutes, then answer it correctly
                val pick = if (missed.add(card.id)) card.choices.first { it != card.correctChoice } else card.correctChoice
                s = c.advance(c.answer(s, pick))
                if (s.currentCard == null) { now += 11 * 60_000; s = c.startSession(GameMode.ARTICLE) }
            }
        }
        assertTrue(c.dailyProgress().reached)
        assertEquals(4, c.dailyProgress().answered)   // 2 cards x (1 miss + 1 correct), past the goal of 3
    }

    @Test fun dayCountResetsOnANewDayButHistoryIsKeptAndGoalSnapsToTheSlider() {
        val t = dev.yuji.quiz.data.InMemoryTrainingStore()
        val c = controller(t)
        var s = c.startSession(GameMode.PREPOSITION)
        c.answer(s, s.currentCard!!.correctChoice)
        assertEquals(1, c.dailyProgress().answered)
        val firstDay = now / DAY_MILLIS
        now += DAY_MILLIS
        assertEquals(0, c.dailyProgress().answered)
        assertEquals(mapOf(firstDay to 1), t.history)
        c.setDailyGoal(37); assertEquals(40, c.dailyProgress().goal)
        c.setDailyGoal(500); assertEquals(100, c.dailyProgress().goal)
        c.setDailyGoal(1); assertEquals(10, c.dailyProgress().goal)
    }

    @Test fun calendarReflectsAnswersAndStreak() {
        val c = controller(dev.yuji.quiz.data.InMemoryTrainingStore())
        val modes = listOf(GameMode.ARTICLE, GameMode.ARTICLE, GameMode.PREPOSITION)   // answered cards wait 3 days
        repeat(3) { day ->
            val s = c.startSession(modes[day])
            c.answer(s, s.currentCard!!.correctChoice)
            if (day < 2) now += DAY_MILLIS
        }
        val cal = c.calendar(c.currentMonth())
        assertEquals(3, cal.currentStreak)
        assertEquals(3, cal.trainedDaysInMonth)
        assertEquals(3, cal.answersInMonth)
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

package dev.yuji.quiz

import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.content.NounEntry
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.CardPhase
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.MINUTE_MILLIS
import dev.yuji.quiz.domain.StudySession
import org.junit.Assert.assertEquals
import org.junit.Test

/** Follows one card through days of study with the real controller and a fake clock. */
class PeriodizationSimulationTest {
    private var now = 1_000 * DAY_MILLIS
    private val store = InMemoryProgressStore()
    private val deck = CardFactory.buildDeck(listOf(NounEntry("der", "Tisch", "Tische")), emptyList())
    private var c = StudyController(deck, store, Clock { now }, NoopStudyLog)
    private val id = "article:Tisch"

    private fun progress() = store.loadAll().getValue(id)
    private fun stats() = c.overview().first { it.mode == GameMode.ARTICLE }.stats

    /** Opens a session, thinks for [seconds], answers, and returns the session after feedback. */
    private fun study(correct: Boolean, seconds: Long = 5): StudySession {
        var s = c.startSession(GameMode.ARTICLE)
        assertEquals(id, s.currentCard!!.id)
        now += seconds * 1000
        val card = s.currentCard!!
        val pick = if (correct) card.correctChoice else card.choices.first { it != card.correctChoice }
        s = c.answer(s, pick)
        return s
    }

    @Test fun firstCorrectAnswerSendsTheCardAwayForThreeDays() {
        val s1 = study(true)
        assertEquals(CardPhase.REVIEW, progress().phase)
        assertEquals(3, progress().intervalDays)
        assertEquals(3 * DAY_MILLIS, progress().dueAtMillis - now)
        assertEquals("card does not come back in the same session", 1, s1.queue.size)
        assertEquals(0, stats().reviewsDue)
        now += 3 * DAY_MILLIS - MINUTE_MILLIS
        assertEquals("not due one minute early", 0, stats().reviewsDue)
        now += MINUTE_MILLIS
        assertEquals(1, stats().reviewsDue)
    }

    @Test fun firstWrongAnswerWaitsTenMinutesAndIsNotOfferedEarlierWhenYouComeBack() {
        val s1 = study(false)
        assertEquals(CardPhase.LEARNING, progress().phase)
        assertEquals(10 * MINUTE_MILLIS, progress().dueAtMillis - now)
        assertEquals("missed card does not come straight back in this session", 1, s1.queue.size)
        now += 9 * MINUTE_MILLIS
        assertEquals("leaving and returning within 10 minutes: not offered", 0, stats().learningDue)
        now += MINUTE_MILLIS
        assertEquals("after 10 minutes it is the first card", 1, stats().learningDue)
        study(true)
        assertEquals(CardPhase.REVIEW, progress().phase)
        assertEquals(3, progress().intervalDays)
    }

    @Test fun reviewIntervalsGrowLikeAnki() {
        study(true)                                                      // first correct answer: 3 days
        val seen = mutableListOf(progress().intervalDays)
        repeat(4) {
            now += progress().intervalDays * DAY_MILLIS - MINUTE_MILLIS
            assertEquals("not due one minute early", 0, stats().reviewsDue)
            now += MINUTE_MILLIS
            assertEquals(1, stats().reviewsDue)
            study(true)
            seen += progress().intervalDays
        }
        assertEquals("every correct answer pushes the card further out", listOf(3, 8, 20, 50, 125), seen)
        assertEquals(2.5, progress().easeFactor, 1e-9)
    }

    @Test fun lapseSendsCardToRelearningAndResetsInterval() {
        study(true)
        repeat(2) { now += progress().intervalDays * DAY_MILLIS; study(true) }   // 3, 8, 20 days
        assertEquals(20, progress().intervalDays)

        now += 20 * DAY_MILLIS
        study(false)
        assertEquals(CardPhase.RELEARNING, progress().phase)
        assertEquals(1, progress().lapses)
        assertEquals(2.3, progress().easeFactor, 1e-9)
        assertEquals(1, progress().intervalDays)
        now += 10 * MINUTE_MILLIS
        study(true)
        assertEquals(CardPhase.REVIEW, progress().phase)
        now += DAY_MILLIS
        study(true)
        assertEquals("interval restarts small with the lowered ease", 2, progress().intervalDays)
    }

    @Test fun slowAnswerIsHardAndFastAnswerOnMatureCardIsEasy() {
        study(true)
        now += 3 * DAY_MILLIS
        study(true, seconds = 12)                                       // HARD: ease -0.15, 3 * 1.2 = 4 days
        assertEquals(4, progress().intervalDays)
        assertEquals(2.35, progress().easeFactor, 1e-9)

        now += 4 * DAY_MILLIS
        study(true, seconds = 1)                                        // EASY: ease +0.15
        assertEquals(2.5, progress().easeFactor, 1e-9)
        assertEquals("4d * 2.35 * 1.3 = 12d, more than GOOD would give (9d)", 12, progress().intervalDays)
    }

    @Test fun firstEverFastAnswerIsNotEasyAndGetsTheNormalThreeDays() {
        study(true, seconds = 1)
        assertEquals(3, progress().intervalDays)
    }

    /** The real user flow: miss a card, leave the quiz, come back (a new session), at different times. */
    @Test fun leavingAndReturningNeverShowsAMissedCardEarly() {
        study(false)
        now += MINUTE_MILLIS
        assertEquals("1 min later: nothing to study", null, c.startSession(GameMode.ARTICLE).currentCard)
        now += 8 * MINUTE_MILLIS
        assertEquals("9 min later: still nothing", null, c.startSession(GameMode.ARTICLE).currentCard)
        now += MINUTE_MILLIS
        assertEquals("10 min later: the missed card", id, c.startSession(GameMode.ARTICLE).currentCard!!.id)
    }
}

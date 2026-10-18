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

    @Test fun learningStepsThenGraduationToOneDay() {
        val s1 = study(true)
        assertEquals(CardPhase.LEARNING, progress().phase)
        assertEquals(10 * MINUTE_MILLIS, progress().dueAtMillis - now)
        assertEquals("10-min card comes back inside the same session (learn-ahead)", 2, s1.queue.size)

        assertEquals("nothing due yet beyond the learn-ahead window is still offered", 1, stats().learningDue)
        now += 10 * MINUTE_MILLIS
        val s2 = study(true)
        assertEquals(CardPhase.REVIEW, progress().phase)
        assertEquals(1, progress().intervalDays)
        assertEquals("graduated card leaves the session", 1, s2.queue.size)
    }

    @Test fun reviewIntervalsGrowLikeAnki() {
        study(true); now += 10 * MINUTE_MILLIS; study(true)             // graduate: 1 day
        val seen = mutableListOf(progress().intervalDays)
        repeat(4) {
            now += progress().intervalDays * DAY_MILLIS - MINUTE_MILLIS
            assertEquals("not due one minute early", 0, stats().reviewsDue)
            now += MINUTE_MILLIS
            assertEquals(1, stats().reviewsDue)
            study(true)
            seen += progress().intervalDays
        }
        assertEquals(listOf(1, 3, 8, 20, 50), seen)
        assertEquals(2.5, progress().easeFactor, 1e-9)
    }

    @Test fun lapseSendsCardToRelearningAndResetsInterval() {
        study(true); now += 10 * MINUTE_MILLIS; study(true)
        repeat(3) { now += progress().intervalDays * DAY_MILLIS; study(true) }   // 3, 8, 20 days
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
        study(true); now += 10 * MINUTE_MILLIS; study(true)
        now += DAY_MILLIS
        study(true, seconds = 12)                                       // HARD: ease -0.15, 1 * 1.2 -> floor old+1
        assertEquals(2, progress().intervalDays)
        assertEquals(2.35, progress().easeFactor, 1e-9)

        now += 2 * DAY_MILLIS
        study(true, seconds = 1)                                        // EASY: ease +0.15
        assertEquals(2.5, progress().easeFactor, 1e-9)
        assertEquals("2d * 2.35 * 1.3 = 6d, more than GOOD would give (5d)", 6, progress().intervalDays)
    }

    @Test fun firstEverFastAnswerIsNotEasyAndStillGoesThroughLearning() {
        study(true, seconds = 1)
        assertEquals(CardPhase.LEARNING, progress().phase)
    }
}

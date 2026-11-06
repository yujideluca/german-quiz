package dev.yuji.quiz

// REQ FS-2 FS-4 SP-1 SP-2

import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.content.FspCase
import dev.yuji.quiz.content.FspCaseRun
import dev.yuji.quiz.content.FspStep
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.CardPhase
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.MINUTE_MILLIS
import dev.yuji.quiz.domain.ReviewGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FspCaseControllerTest {
    private var now = 100 * DAY_MILLIS
    private val clock = Clock { now }

    private fun case(id: String, area: String, steps: Int = 10) = FspCase(
        id, area, "Beschwerde $id", "Diagnose $id", "common_FSP", 0, "Patient $id",
        List(steps) { FspStep(1 + it * 3 / steps, "S$it", null, "Frage $it?", listOf("a$it", "b$it", "c$it", "d$it"), it % 4,
            List(4) { j -> if (j == it % 4) "Richtig." else "Falsch." }, null) },
    )
    private val cases = listOf(case("k1", "Kardiologie"), case("k2", "Kardiologie"), case("p1", "Pneumologie"))
    private val deck = CardFactory.buildDeck(emptyList(), emptyList(), fspCases = cases)
    private val store = InMemoryProgressStore()
    private val controller = StudyController(deck, store, clock, NoopStudyLog, cases = cases.associateBy { it.id })

    /** Plays the current case with [rightSteps] steps right at the first try. */
    private fun play(run: FspCaseRun, rightSteps: Int): FspCaseRun {
        var r = run
        r.case.steps.forEachIndexed { i, s ->
            if (i >= rightSteps) r = r.pick((s.answer + 1) % 4)
            r = r.pick(s.answer).next()
        }
        return r
    }

    @Test fun newCasesComeInTheOrderOfTheAreas() {
        var s = controller.startSession(GameMode.FSP_CASE)
        val seen = mutableListOf<String>()
        while (!s.isFinished) {
            seen += s.currentCard!!.id
            s = controller.finishCase(s, play(controller.startCase(s)!!, 10))
            s = controller.advance(s)
        }
        assertEquals(listOf("fsp:k1", "fsp:k2", "fsp:p1"), seen)
    }

    @Test fun aPassedCaseReturnsAfterThreeDaysHoweverLongItTook() {
        var s = controller.startSession(GameMode.FSP_CASE)
        val run = play(controller.startCase(s)!!, 8)          // 80 % passes
        now += 45 * MINUTE_MILLIS                               // a slow case is not graded down
        s = controller.finishCase(s, run)
        val a = s.lastAnswer!!
        assertTrue(a.correct)
        assertEquals(ReviewGrade.GOOD, a.grade)
        assertEquals(now + 3 * DAY_MILLIS, a.after.dueAtMillis)
        assertEquals(a.after, store.loadAll().getValue("fsp:k1"))
    }

    @Test fun aFailedCaseReturnsAfterTenMinutesAndNotInTheSameSession() {
        var s = controller.startSession(GameMode.FSP_CASE)
        s = controller.finishCase(s, play(controller.startCase(s)!!, 7))   // 70 % fails
        val a = s.lastAnswer!!
        assertFalse(a.correct)
        assertEquals(ReviewGrade.AGAIN, a.grade)
        assertEquals(now + 10 * MINUTE_MILLIS, a.after.dueAtMillis)
        s = controller.advance(s)
        assertFalse("not re-queued in the same session", s.queue.any { it.id == "fsp:k1" })
        assertFalse(controller.startSession(GameMode.FSP_CASE).queue.any { it.id == "fsp:k1" })
        now += 10 * MINUTE_MILLIS
        assertEquals("fsp:k1", controller.startSession(GameMode.FSP_CASE).currentCard!!.id)
    }

    @Test fun aReviewCaseIsGradedByItsScore() {
        var s = controller.startSession(GameMode.FSP_CASE)
        s = controller.finishCase(s, play(controller.startCase(s)!!, 10))
        now += 3 * DAY_MILLIS
        s = controller.startSession(GameMode.FSP_CASE)
        assertEquals("fsp:k1", s.currentCard!!.id)
        assertEquals(CardPhase.REVIEW, store.loadAll().getValue("fsp:k1").phase)
        s = controller.finishCase(s, play(controller.startCase(s)!!, 8))
        assertEquals(ReviewGrade.HARD, s.lastAnswer!!.grade)
    }

    @Test fun anUnfinishedOrForeignCaseIsIgnored() {
        val s = controller.startSession(GameMode.FSP_CASE)
        val run = controller.startCase(s)!!
        assertSame(s, controller.finishCase(s, run))                         // not finished
        val other = play(FspCaseRun(cases[2], 1), 10)
        assertSame(s, controller.finishCase(s, other))                       // not the current card
        assertNull(controller.startCase(controller.startSession(GameMode.ARTICLE)))
    }
}

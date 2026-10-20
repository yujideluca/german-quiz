package dev.yuji.quiz

// REQ DG-1 DG-2 DG-3 DG-4

import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.content.NounEntry
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.data.InMemoryTrainingStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.CardStatus
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.StudySession
import dev.yuji.quiz.domain.SuspensionDuration
import java.time.ZoneOffset
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Property-based test for the progress bar. Random player behaviour (answers right and wrong, repeated taps,
 * removing and restoring cards, resetting card progress, changing the goal, waiting past midnight, restarting
 * the app, restoring a backup) is replayed through the real [StudyController]. After every step the bar is
 * compared with an independent oracle that knows only the requirements: a plain tally of accepted answers per
 * day and a goal on the slider's grid.
 */
class DailyProgressPropertyTest {
    private val deck = CardFactory.buildDeck((1..60).map { NounEntry(if (it % 2 == 0) "der" else "die", "Wort$it", "Worte$it") }, emptyList())

    private fun run(seed: Int, steps: Int) {
        val rnd = Random(seed)
        var now = 100 * DAY_MILLIS + rnd.nextLong(DAY_MILLIS)
        val progress = InMemoryProgressStore()
        val training = InMemoryTrainingStore()
        fun controller() = StudyController(deck, progress, Clock { now }, NoopStudyLog, zone = ZoneOffset.UTC, training = training)
        var c = controller()
        var session: StudySession = c.startSession(GameMode.ARTICLE)

        val tally = mutableMapOf<Long, Int>()
        var goal = 20
        fun fail(step: Int, what: String): Nothing = throw AssertionError("seed=$seed step=$step: $what")

        repeat(steps) { step ->
            val today = now / DAY_MILLIS
            when (rnd.nextInt(10)) {
                0, 1, 2, 3 -> {                                   // answer (right or wrong); counts once
                    val card = session.currentCard
                    if (card != null && !session.isAwaitingAdvance) {
                        val pick = if (rnd.nextBoolean()) card.correctChoice else card.choices.first { it != card.correctChoice }
                        session = c.answer(session, pick)
                        tally[today] = (tally[today] ?: 0) + 1
                    }
                }
                4 -> {                                            // repeated tap on an answer: ignored
                    val card = session.currentCard
                    if (card != null && session.isAwaitingAdvance) session = c.answer(session, card.choices.first())
                }
                5 -> session = if (session.isAwaitingAdvance) c.advance(session) else session
                6 -> when (rnd.nextInt(4)) {                      // card management never changes the count
                    0 -> if (session.currentCard != null && !session.isAwaitingAdvance) session = c.suspendCurrentCard(session, SuspensionDuration.ONE_WEEK)
                    1 -> c.restoreAll(CardStatus.SUSPENDED)
                    2 -> c.resetAllProgress()
                    else -> c.restoreAll(CardStatus.REMOVED)
                }
                7 -> { goal = rnd.nextInt(-50, 400); c.setDailyGoal(goal) }
                8 -> {                                            // time passes, possibly across midnight
                    now += rnd.nextLong(0, 2 * DAY_MILLIS + 1)
                    if (rnd.nextInt(3) == 0) session = c.startSession(GameMode.ARTICLE)
                }
                else -> {                                         // app restart, or backup export + import
                    if (rnd.nextBoolean()) c = controller()
                    else c.importBackup(c.exportBackup("test", null))
                    session = c.startSession(GameMode.ARTICLE)
                }
            }
            // oracle
            val expectedGoal = Math.round(goal / 10.0).toInt().times(10).coerceIn(10, 100)
            val expectedAnswered = tally[now / DAY_MILLIS] ?: 0
            val d = c.dailyProgress()
            if (d.answered != expectedAnswered) fail(step, "answered ${d.answered}, expected $expectedAnswered")
            if (d.goal != expectedGoal) fail(step, "goal ${d.goal}, expected $expectedGoal")
            val shouldBe = minOf(1f, d.answered.toFloat() / d.goal)
            if (d.fraction.isNaN() || d.fraction < 0f || d.fraction > 1f) fail(step, "fraction ${d.fraction} outside 0..1")
            if (d.fraction != shouldBe) fail(step, "fraction ${d.fraction}, expected $shouldBe")
            if (d.reached != (d.answered >= d.goal)) fail(step, "reached=${d.reached} with ${d.answered}/${d.goal}")
            if (d.reached != (d.fraction == 1f)) fail(step, "goal text and bar disagree (${d.answered}/${d.goal}, bar ${d.fraction})")
        }
    }

    @Test fun theBarFollowsTheOracleInTwoThousandRandomHistories() {
        for (seed in 1..2_000) run(seed, steps = 80)
    }

    @Test fun withNoAnswersTheBarIsEmptyAndAtTheGoalItIsFull() {
        val training = InMemoryTrainingStore(dailyGoal = 10)
        var now = 100 * DAY_MILLIS
        val c = StudyController(deck, InMemoryProgressStore(), Clock { now }, NoopStudyLog, zone = ZoneOffset.UTC, training = training)
        assertEquals(0f, c.dailyProgress().fraction, 0f)
        assertFalse(c.dailyProgress().reached)
        var s = c.startSession(GameMode.ARTICLE)
        repeat(9) { s = c.advance(c.answer(s, s.currentCard!!.correctChoice)) }
        assertEquals(0.9f, c.dailyProgress().fraction, 1e-6f); assertFalse(c.dailyProgress().reached)
        s = c.advance(c.answer(s, s.currentCard!!.correctChoice))
        assertEquals(1f, c.dailyProgress().fraction, 0f); assertTrue(c.dailyProgress().reached)
        now += 1                                              // still the same day
        assertEquals(10, c.dailyProgress().answered)
    }

    @Test fun midnightStartsAnEmptyBarToTheMinute() {
        var now = 100 * DAY_MILLIS + DAY_MILLIS - 1
        val c = StudyController(deck, InMemoryProgressStore(), Clock { now }, NoopStudyLog, zone = ZoneOffset.UTC)
        val s = c.startSession(GameMode.ARTICLE)
        c.answer(s, s.currentCard!!.correctChoice)
        assertEquals(1, c.dailyProgress().answered)
        now += 1
        assertEquals(0, c.dailyProgress().answered)
        now -= 1                                              // clock set back (time zone change): yesterday's count again
        assertEquals(1, c.dailyProgress().answered)
    }
}

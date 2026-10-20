package dev.yuji.quiz

// REQ CT-1 SP-1 SP-2 SE-3

import androidx.test.platform.app.InstrumentationRegistry
import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.data.AssetVocabularySource
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.MINUTE_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance (validation) test on a real device: the app's real vocabulary assets go through every mode of
 * the real controller, with an in-memory store, so the phone's own study data is never touched.
 * For each mode it plays the two situations Giovanni cares about most and checks what the *player* is offered.
 */
class StudyFlowAcceptanceTest {
    private var now = 5_000 * DAY_MILLIS
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = AssetVocabularySource(ctx)
    private val deck = CardFactory.buildDeck(source.nouns(), source.prepositions(), source.verbs(), source.errorSentences())

    private fun controller() = StudyController(deck, InMemoryProgressStore(), Clock { now }, NoopStudyLog)

    @Test fun everyModeLoadsFromTheRealAssetsAndStartsWithOnlyNewCards() {
        val c = controller()
        for (o in c.overview()) {
            assertTrue("${o.mode} has cards", o.totalCards > 0)
            assertEquals("${o.mode}: all new", o.totalCards, o.stats.newAvailable)
            assertEquals("${o.mode}: nothing due", 0, o.stats.reviewsDue + o.stats.learningDue)
        }
    }

    @Test fun aWrongAnswerComesBackAfterTenMinutesAndACorrectOneAfterThreeDays_inEveryMode() {
        for (mode in GameMode.entries) {
            val c = controller()
            var s = c.startSession(mode)
            val missed = s.currentCard!!
            now += 4_000
            s = c.advance(c.answer(s, missed.choices.first { it != missed.correctChoice }))
            val right = s.currentCard!!
            now += 4_000
            c.advance(c.answer(s, right.correctChoice))
            val missedAt = now - 4_000

            // leaving and re-opening shortly after: the missed card is not offered
            now = missedAt + 9 * MINUTE_MILLIS
            assertTrue("$mode: missed card must not be offered after 9 min", c.startSession(mode).queue.none { it.id == missed.id })
            now = missedAt + 10 * MINUTE_MILLIS
            assertEquals("$mode: after 10 min the missed card is the first one", missed.id, c.startSession(mode).currentCard!!.id)

            // the correct card stays away for 3 days
            now = missedAt + 4_000 + 3 * DAY_MILLIS - 1
            assertTrue("$mode: correct card must not be offered before day 3", c.startSession(mode).queue.none { it.id == right.id })
            now += 1
            assertNotNull(c.startSession(mode).queue.firstOrNull { it.id == right.id })
        }
    }
}

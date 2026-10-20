package dev.yuji.quiz.ui

// REQ DG-2 DG-3 DG-4

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import dev.yuji.quiz.StudyController
import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.data.AssetVocabularySource
import dev.yuji.quiz.data.InMemoryProgressStore
import dev.yuji.quiz.data.InMemoryTrainingStore
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.Clock
import dev.yuji.quiz.domain.DAY_MILLIS
import dev.yuji.quiz.domain.GameMode
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Acceptance test on the device for the progress bar as the *player* meets it: the real view model with the
 * real vocabulary, an in-memory training store (the phone's own study data stays untouched) and a fake clock.
 * The home screen is a snapshot, so the bar must be refreshed when the app returns to the foreground.
 */
class DailyBarAcceptanceTest {
    private var now = 5_000 * DAY_MILLIS + 20 * 3_600_000          // 20:00 UTC
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private fun viewModel(training: InMemoryTrainingStore): StudyViewModel {
        var vm: StudyViewModel? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = StudyViewModel(app) { application, _ ->
                val source = AssetVocabularySource(application)
                StudyController(
                    CardFactory.buildDeck(source.nouns(), source.prepositions(), source.verbs(), source.errorSentences()),
                    InMemoryProgressStore(), Clock { now }, NoopStudyLog, zone = ZoneOffset.UTC, training = training,
                )
            }
        }
        return vm!!
    }

    private fun home(vm: StudyViewModel) = (vm.state.value as UiState.ModeSelect).daily

    @Test fun theBarCountsAnswersAndStartsEmptyAfterMidnightWhenTheAppComesBack() {
        val vm = viewModel(InMemoryTrainingStore(dailyGoal = 10))
        assertEquals(0f, home(vm).fraction, 0f)

        val instr = InstrumentationRegistry.getInstrumentation()
        instr.runOnMainSync { vm.start(GameMode.ARTICLE) }
        repeat(3) {
            instr.runOnMainSync {
                val s = (vm.state.value as UiState.Studying).session
                vm.answer(if (it == 1) s.currentCard!!.choices.first { c -> c != s.currentCard!!.correctChoice } else s.currentCard!!.correctChoice)
                vm.next()
            }
        }
        instr.runOnMainSync { vm.backToModes() }
        assertEquals("3 answers, right and wrong alike, fill 3 of 10", 0.3f, home(vm).fraction, 1e-6f)

        now += 5 * 3_600_000                                         // the phone sleeps past midnight
        assertEquals("snapshot is still yesterday's until the app is resumed", 3, home(vm).answered)
        instr.runOnMainSync { vm.refreshHome() }                      // what MainActivity does on ON_RESUME
        assertEquals("a new day starts with an empty bar", 0, home(vm).answered)
        assertEquals(0f, home(vm).fraction, 0f)
    }

    @Test fun resumingWhileStudyingDoesNotDisturbTheRunningQuiz() {
        val vm = viewModel(InMemoryTrainingStore())
        val instr = InstrumentationRegistry.getInstrumentation()
        instr.runOnMainSync { vm.start(GameMode.ARTICLE) }
        val before = vm.state.value
        instr.runOnMainSync { vm.refreshHome() }
        assertEquals(before, vm.state.value)
    }
}

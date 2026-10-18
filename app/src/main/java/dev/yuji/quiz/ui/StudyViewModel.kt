package dev.yuji.quiz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yuji.quiz.BuildConfig
import dev.yuji.quiz.ModeOverview
import dev.yuji.quiz.StudyController
import dev.yuji.quiz.android.AndroidStudyLog
import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.data.AssetVocabularySource
import dev.yuji.quiz.data.FileProgressStore
import dev.yuji.quiz.data.SharedPreferencesSettingsStore
import dev.yuji.quiz.data.SharedPreferencesTrainingStore
import dev.yuji.quiz.data.ThemeMode
import dev.yuji.quiz.debug.DebugFlags
import dev.yuji.quiz.domain.ActivityCalendar
import dev.yuji.quiz.domain.CardStatus
import dev.yuji.quiz.domain.DailyProgress
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.ManagedCard
import dev.yuji.quiz.domain.ManagedCounts
import dev.yuji.quiz.domain.StudySession
import dev.yuji.quiz.domain.SuspensionDuration
import dev.yuji.quiz.domain.SystemClock
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState {
    data class ModeSelect(val modes: List<ModeOverview>, val daily: DailyProgress, val calendar: ActivityCalendar) : UiState
    data class Studying(val session: StudySession, val choices: List<String>, val daily: DailyProgress) : UiState
    data class Finished(val session: StudySession) : UiState
    data class Settings(
        val themeMode: ThemeMode,
        val dailyGoal: Int,
        val counts: ManagedCounts,
        val query: String,
        val results: List<ManagedCard>,
    ) : UiState
    data class Failed(val message: String) : UiState
}

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private var advanceJob: Job? = null
    private var searchQuery = ""
    private var calendarMonth: java.time.YearMonth? = null

    val debugFlags: DebugFlags = DebugFlags.forBuild(BuildConfig.DEBUG)
    private val log = AndroidStudyLog(debugFlags)
    private val settingsStore = SharedPreferencesSettingsStore(app)

    private val _themeMode = MutableStateFlow(settingsStore.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val controller: StudyController? = runCatching {
        val source = AssetVocabularySource(app)
        StudyController(
            deck = CardFactory.buildDeck(source.nouns(), source.prepositions()),
            store = FileProgressStore(File(app.filesDir, "progress.json"), log),
            clock = SystemClock,
            log = log,
            training = SharedPreferencesTrainingStore(app),
        )
    }.onFailure { android.util.Log.e("Quiz", "failed to initialise", it) }.getOrNull()

    private val _state = MutableStateFlow<UiState>(
        controller?.let { modeSelectState(it) } ?: UiState.Failed("Could not load vocabulary")
    )
    val state: StateFlow<UiState> = _state

    // ---- study ------------------------------------------------------------------------------

    fun start(mode: GameMode) = withController { c -> render(c, c.startSession(mode)) }

    fun answer(choice: String) = withController { c ->
        val studying = _state.value as? UiState.Studying ?: return@withController
        val updated = c.answer(studying.session, choice)
        render(c, updated)
        if (updated.isAwaitingAdvance) scheduleAdvance()
    }

    /** Suspend for a week / month or remove permanently; the card leaves the running session. */
    fun suspendCurrentCard(duration: SuspensionDuration) = withController { c ->
        val studying = _state.value as? UiState.Studying ?: return@withController
        advanceJob?.cancel()
        render(c, c.suspendCurrentCard(studying.session, duration))
    }

    /** Shows the right/wrong feedback briefly, then moves on without a tap. */
    private fun scheduleAdvance() {
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(AUTO_ADVANCE_DELAY_MILLIS)
            next()
        }
    }

    private fun next() = withController { c ->
        (_state.value as? UiState.Studying)?.let { render(c, c.advance(it.session)) }
    }

    fun backToModes() = withController { c ->
        advanceJob?.cancel()
        calendarMonth = null                                        // home always opens on the current month
        _state.update { modeSelectState(c) }
    }

    // ---- calendar ---------------------------------------------------------------------------

    fun showPreviousMonth() = shiftMonth(-1)
    fun showNextMonth() = shiftMonth(1)

    private fun shiftMonth(delta: Long) = withController { c ->
        val target = (calendarMonth ?: c.currentMonth()).plusMonths(delta)
        if (target > c.currentMonth()) return@withController      // no future months
        calendarMonth = target
        _state.update { if (it is UiState.ModeSelect) modeSelectState(c) else it }
    }

    private fun modeSelectState(c: StudyController) =
        UiState.ModeSelect(c.overview(), c.dailyProgress(), c.calendar(calendarMonth ?: c.currentMonth()))

    // ---- settings ---------------------------------------------------------------------------

    fun openSettings() = withController { c ->
        searchQuery = ""
        _state.update { settingsState(c) }
    }

    fun setThemeMode(mode: ThemeMode) = withController { c ->
        settingsStore.themeMode = mode
        _themeMode.value = mode
        _state.update { settingsState(c) }
    }

    fun setDailyGoal(goal: Int) = settingsAction { it.setDailyGoal(goal) }

    fun setSearchQuery(query: String) = withController { c ->
        searchQuery = query
        _state.update { settingsState(c) }
    }

    fun restoreCard(cardId: String) = settingsAction { it.restoreCard(cardId) }
    fun restoreAllSuspended() = settingsAction { it.restoreAll(CardStatus.SUSPENDED) }
    fun restoreAllRemoved() = settingsAction { it.restoreAll(CardStatus.REMOVED) }
    fun resetAllProgress() = settingsAction { it.resetAllProgress() }

    private inline fun settingsAction(block: (StudyController) -> Unit) = withController { c ->
        block(c)
        _state.update { settingsState(c) }
    }

    private fun settingsState(c: StudyController) =
        UiState.Settings(_themeMode.value, c.dailyProgress().goal, c.managedCounts(), searchQuery, c.managedCards(searchQuery))

    // ---- plumbing ---------------------------------------------------------------------------

    private fun render(c: StudyController, session: StudySession) = _state.update {
        if (session.isFinished) UiState.Finished(session) else UiState.Studying(session, c.choices(session), c.dailyProgress())
    }

    private inline fun withController(block: (StudyController) -> Unit) {
        controller?.let(block)
    }

    private companion object {
        const val AUTO_ADVANCE_DELAY_MILLIS = 500L
    }
}

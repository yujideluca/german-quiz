package dev.yuji.quiz.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yuji.quiz.BuildConfig
import dev.yuji.quiz.R
import dev.yuji.quiz.android.withLanguage
import dev.yuji.quiz.data.AppLanguage
import dev.yuji.quiz.data.UpdateFailure
import dev.yuji.quiz.data.UpdateFailureKind
import dev.yuji.quiz.ModeOverview
import dev.yuji.quiz.StudyController
import dev.yuji.quiz.android.AndroidStudyLog
import dev.yuji.quiz.android.ApkInstaller
import dev.yuji.quiz.content.CardFactory
import dev.yuji.quiz.data.AssetVocabularySource
import dev.yuji.quiz.data.FileProgressStore
import dev.yuji.quiz.data.SharedPreferencesSettingsStore
import dev.yuji.quiz.data.SharedPreferencesTrainingStore
import dev.yuji.quiz.data.UpdateClient
import dev.yuji.quiz.data.ThemeMode
import dev.yuji.quiz.debug.DebugFlags
import dev.yuji.quiz.debug.StudyLog
import dev.yuji.quiz.domain.ActivityCalendar
import dev.yuji.quiz.domain.BackupParse
import dev.yuji.quiz.domain.BackupProblem
import dev.yuji.quiz.domain.ProgressBackup
import dev.yuji.quiz.domain.decodeBackup
import dev.yuji.quiz.domain.encodeBackup
import dev.yuji.quiz.domain.CardStatus
import dev.yuji.quiz.domain.DailyProgress
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.ManagedCard
import dev.yuji.quiz.domain.ManagedCounts
import dev.yuji.quiz.domain.StudySession
import dev.yuji.quiz.domain.SuspensionDuration
import dev.yuji.quiz.domain.SystemClock
import dev.yuji.quiz.domain.UpdateManifest
import dev.yuji.quiz.domain.shouldCheckForUpdate
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data class ModeSelect(val modes: List<ModeOverview>, val daily: DailyProgress, val calendar: ActivityCalendar) : UiState
    data class Studying(val session: StudySession, val choices: List<String>, val daily: DailyProgress) : UiState
    data class Finished(val session: StudySession) : UiState
    data class Settings(
        val themeMode: ThemeMode,
        val language: AppLanguage,
        val dailyGoal: Int,
        val counts: ManagedCounts,
        val query: String,
        val results: List<ManagedCard>,
    ) : UiState
    data class Failed(val message: String) : UiState
}

/** Progress of the "update from GitHub" flow, shown in Settings. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest) : UpdateState
    data class NeedsPermission(val manifest: UpdateManifest) : UpdateState
    data class DownloadFailed(val manifest: UpdateManifest, val message: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class StudyViewModel @JvmOverloads constructor(
    app: Application,
    /** Builds the controller; tests pass one with in-memory stores and a fake clock. */
    controllerFactory: ((Application, StudyLog) -> StudyController)? = null,
) : AndroidViewModel(app) {
    private var advanceJob: Job? = null
    private var searchQuery = ""
    private var calendarMonth: java.time.YearMonth? = null

    val debugFlags: DebugFlags = DebugFlags.forBuild(BuildConfig.DEBUG)
    private val log = AndroidStudyLog(debugFlags)
    private val settingsStore = SharedPreferencesSettingsStore(app)

    private val _themeMode = MutableStateFlow(settingsStore.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _language = MutableStateFlow(settingsStore.language)
    val language: StateFlow<AppLanguage> = _language

    /** Text for messages the view model produces itself (notices, errors), in the language chosen in Settings. */
    private fun t(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().withLanguage(settingsStore.language).getString(id, *args)

    private val controller: StudyController? = runCatching {
        controllerFactory?.invoke(app, log) ?: run {
            val source = AssetVocabularySource(app)
            StudyController(
                deck = CardFactory.buildDeck(source.nouns(), source.prepositions(), source.verbs(), source.errorSentences()),
                store = FileProgressStore(File(app.filesDir, "progress.json"), log),
                clock = SystemClock,
                log = log,
                training = SharedPreferencesTrainingStore(app),
            )
        }
    }.onFailure { android.util.Log.e("Quiz", "failed to initialise", it) }.getOrNull()

    private val _state = MutableStateFlow<UiState>(
        controller?.let { modeSelectState(it) } ?: UiState.Failed(t(R.string.load_failed))
    )
    val state: StateFlow<UiState> = _state

    // ---- study ------------------------------------------------------------------------------

    fun start(mode: GameMode) = withController { c -> render(c, c.startSession(mode)) }

    fun answer(choice: String) = withController { c ->
        val studying = _state.value as? UiState.Studying ?: return@withController
        val updated = c.answer(studying.session, choice)
        render(c, updated)
        if (updated.isAwaitingAdvance && updated.mode.autoAdvances) scheduleAdvance()
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

    /** Moves to the next question; used by the "Next" button of modes that do not advance by themselves. */
    fun next() = withController { c ->
        (_state.value as? UiState.Studying)?.let { render(c, c.advance(it.session)) }
    }

    /**
     * The home screen is a snapshot (daily bar, due counts, calendar). Call when the app returns to the
     * foreground so a snapshot taken yesterday never shows yesterday's bar after midnight.
     */
    fun refreshHome() = withController { c ->
        _state.update { if (it is UiState.ModeSelect) modeSelectState(c) else it }
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

    /** Takes effect after the activity restarts (MainActivity watches [language] and recreates itself). */
    fun setLanguage(language: AppLanguage) {
        settingsStore.language = language
        _language.value = language
        controller?.let { c -> _state.update { settingsState(c) } }
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
        UiState.Settings(_themeMode.value, _language.value, c.dailyProgress().goal, c.managedCounts(), searchQuery, c.managedCards(searchQuery))

    // ---- backup -----------------------------------------------------------------------------

    private var pendingBackup: ProgressBackup? = null
    private val _backup = MutableStateFlow(BackupUi())
    val backup: StateFlow<BackupUi> = _backup

    fun backupFileName() = "german-quiz-progress-${java.time.LocalDate.now()}.json"

    fun exportBackupText(): String? = controller?.let {
        encodeBackup(it.exportBackup(BuildConfig.VERSION_NAME, _themeMode.value.name))
    }

    /** Parses a chosen file and asks for confirmation before anything is replaced. */
    fun readBackup(text: String) {
        when (val parsed = decodeBackup(text)) {
            is BackupParse.Invalid -> {
                pendingBackup = null
                _backup.value = BackupUi(notice = t(when (parsed.problem) {
                    BackupProblem.NOT_A_BACKUP -> R.string.backup_invalid_file
                    BackupProblem.TOO_NEW -> R.string.backup_too_new
                }))
            }
            is BackupParse.Ok -> {
                pendingBackup = parsed.backup
                _backup.value = BackupUi(pending = BackupSummary(
                    parsed.backup.progress.size, parsed.backup.history.size, parsed.backup.exportedAtMillis))
            }
        }
    }

    fun applyPendingBackup() = withController { c ->
        val backup = pendingBackup ?: return@withController
        pendingBackup = null
        c.importBackup(backup)
        backup.themeMode?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }?.let {
            settingsStore.themeMode = it
            _themeMode.value = it
        }
        calendarMonth = null
        _backup.value = BackupUi(notice = t(R.string.backup_restored, backup.progress.size, backup.history.size))
        _state.update { settingsState(c) }
    }

    fun discardPendingBackup() {
        pendingBackup = null
        _backup.value = BackupUi()
    }

    fun setBackupNotice(text: String) { _backup.value = BackupUi(notice = text) }

    // ---- app updates ------------------------------------------------------------------------

    private val updateClient = UpdateClient(ref = BuildConfig.UPDATE_REF)
    private val updateFile = File(app.cacheDir, "updates/update.apk")
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    private var lastCheckAtMillis: Long? = null
    private val _promptDismissed = MutableStateFlow(false)
    /** True after "Later": the popup stays hidden until the app is opened again. */
    val promptDismissed: StateFlow<Boolean> = _promptDismissed

    /** Called every time the app comes to the foreground: asks the feed (at most every 30 s) and re-arms the popup. */
    fun onAppOpened() {
        _promptDismissed.value = false
        if (shouldCheckForUpdate(System.currentTimeMillis(), lastCheckAtMillis)) checkForUpdate()
    }

    fun dismissUpdatePrompt() { _promptDismissed.value = true }

    fun checkForUpdate() {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        lastCheckAtMillis = System.currentTimeMillis()
        _update.value = stateWhileChecking(_update.value)
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { updateClient.fetchManifest() } }
                .withUserMessage(R.string.update_check_failed_default)
            _update.value = stateAfterCheck(_update.value, result, BuildConfig.VERSION_CODE)
        }
    }

    /** Downloads the new APK and opens Android's installer; study progress lives in app data and survives. */
    fun installUpdate(manifest: UpdateManifest) {
        _update.value = UpdateState.Downloading(manifest)
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { updateClient.downloadApk(manifest, updateFile) } }
            _update.value = result.fold(
                onSuccess = {
                    if (ApkInstaller.install(getApplication(), updateFile)) UpdateState.Available(manifest)
                    else UpdateState.NeedsPermission(manifest)
                },
                onFailure = { UpdateState.DownloadFailed(manifest, it.userMessage(R.string.update_download_failed_default)) },
            )
        }
    }

    /** The failure's message in the chosen language; unknown failures keep their own text, or [fallback] when empty. */
    private fun Throwable.userMessage(@StringRes fallback: Int): String = when (this) {
        is UpdateFailure -> when (kind) {
            UpdateFailureKind.MALFORMED_FEED -> t(R.string.update_err_feed)
            UpdateFailureKind.CHECKSUM_MISMATCH -> t(R.string.update_err_checksum)
            UpdateFailureKind.RATE_LIMIT -> t(R.string.update_err_rate_limit)
            UpdateFailureKind.SERVER_ERROR -> t(R.string.update_err_server, httpCode)
        }
        else -> message ?: t(fallback)
    }

    private fun <T> Result<T>.withUserMessage(@StringRes fallback: Int): Result<T> =
        fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(IllegalStateException(it.userMessage(fallback), it)) })

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

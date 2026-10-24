package dev.yuji.quiz.ui

import dev.yuji.quiz.ModeOverview
import dev.yuji.quiz.data.AppLanguage
import dev.yuji.quiz.data.ThemeMode
import dev.yuji.quiz.domain.ActivityCalendar
import dev.yuji.quiz.domain.DailyProgress
import dev.yuji.quiz.domain.ManagedCard
import dev.yuji.quiz.domain.ManagedCounts
import dev.yuji.quiz.domain.StudySession
import dev.yuji.quiz.domain.UpdateManifest

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
        /** Version of the question pool in use, and whether it was downloaded (otherwise built into the app). */
        val poolVersion: Int = 0,
        val poolDownloaded: Boolean = false,
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

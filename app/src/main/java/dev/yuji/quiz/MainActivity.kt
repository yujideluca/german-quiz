package dev.yuji.quiz

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import dev.yuji.quiz.data.ThemeMode
import dev.yuji.quiz.ui.BackupActions
import dev.yuji.quiz.ui.FinishedScreen
import dev.yuji.quiz.ui.ModeSelectScreen
import dev.yuji.quiz.ui.SettingsScreen
import dev.yuji.quiz.ui.StudyScreen
import dev.yuji.quiz.ui.StudyViewModel
import dev.yuji.quiz.ui.UiState
import dev.yuji.quiz.ui.UpdateState

class MainActivity : ComponentActivity() {
    private val vm: StudyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode by vm.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep the status / navigation bar icons readable in both themes.
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                // The surface fills the whole window (behind the system bars); only the content is inset.
                Surface(Modifier.fillMaxSize()) {
                  Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val state by vm.state.collectAsState()
                    val updateState by vm.update.collectAsState()
                    val backupUi by vm.backup.collectAsState()
                    val backupActions = remember {
                        BackupActions(
                            vm::backupFileName, vm::exportBackupText, vm::readBackup,
                            vm::applyPendingBackup, vm::discardPendingBackup, vm::setBackupNotice,
                        )
                    }
                    var dismissedVersion by rememberSaveable { mutableStateOf(0) }
                    (updateState as? UpdateState.Available)?.manifest
                        ?.takeIf { state is UiState.ModeSelect && it.versionCode != dismissedVersion }
                        ?.let { found ->
                            AlertDialog(
                                onDismissRequest = { dismissedVersion = found.versionCode },
                                title = { Text("Update available") },
                                text = { Text("Version ${found.versionName} is ready on GitHub. Your progress is kept.") },
                                confirmButton = {
                                    TextButton(onClick = { dismissedVersion = found.versionCode; vm.installUpdate(found) }) {
                                        Text("Update")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { dismissedVersion = found.versionCode }) { Text("Later") }
                                },
                            )
                        }
                    // Back returns to the home screen from everywhere else; on home it leaves the app.
                    BackHandler(enabled = state !is UiState.ModeSelect && state !is UiState.Failed) { vm.backToModes() }
                    when (val s = state) {
                        is UiState.ModeSelect -> ModeSelectScreen(
                            s.modes, s.daily, s.calendar, { vm.start(it.mode) }, vm::openSettings,
                            vm::showPreviousMonth, vm::showNextMonth,
                        )
                        is UiState.Studying -> StudyScreen(
                            s.session, s.choices, s.daily, vm.debugFlags.showCardOverlay,
                            vm::answer, vm::suspendCurrentCard, vm::backToModes,
                        )
                        is UiState.Finished -> FinishedScreen(s.session, vm::backToModes)
                        is UiState.Settings -> SettingsScreen(
                            s, vm::backToModes, vm::setThemeMode, vm::setDailyGoal, vm::setSearchQuery, vm::restoreCard,
                            vm::restoreAllSuspended, vm::restoreAllRemoved, vm::resetAllProgress,
                            updateState, vm::checkForUpdate, vm::installUpdate,
                            backupUi, backupActions,
                        )
                        is UiState.Failed -> Text(s.message)
                    }
                  }
                }
            }
        }
    }
}

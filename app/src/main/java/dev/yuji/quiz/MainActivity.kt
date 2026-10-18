package dev.yuji.quiz

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.ui.Modifier
import dev.yuji.quiz.data.ThemeMode
import dev.yuji.quiz.ui.FinishedScreen
import dev.yuji.quiz.ui.ModeSelectScreen
import dev.yuji.quiz.ui.SettingsScreen
import dev.yuji.quiz.ui.StudyScreen
import dev.yuji.quiz.ui.StudyViewModel
import dev.yuji.quiz.ui.UiState

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
                    when (val s = state) {
                        is UiState.ModeSelect -> ModeSelectScreen(s.modes, s.daily, { vm.start(it.mode) }, vm::openSettings)
                        is UiState.Studying -> StudyScreen(
                            s.session, s.choices, s.daily, vm.debugFlags.showCardOverlay,
                            vm::answer, vm::suspendCurrentCard, vm::backToModes,
                        )
                        is UiState.Finished -> FinishedScreen(s.session, vm::backToModes)
                        is UiState.Settings -> SettingsScreen(
                            s, vm::backToModes, vm::setThemeMode, vm::setDailyGoal, vm::setSearchQuery, vm::restoreCard,
                            vm::restoreAllSuspended, vm::restoreAllRemoved, vm::resetAllProgress,
                        )
                        is UiState.Failed -> Text(s.message)
                    }
                  }
                }
            }
        }
    }
}

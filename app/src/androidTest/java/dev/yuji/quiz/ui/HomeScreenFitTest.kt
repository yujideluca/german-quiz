package dev.yuji.quiz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.ModeOverview
import dev.yuji.quiz.domain.DailyProgress
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.QueueStats
import dev.yuji.quiz.domain.buildCalendar
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * The home screen must show the calendar, the daily goal and every mode card without scrolling on
 * any common phone. Each device is the *safe area* in dp (screen minus status and navigation bars),
 * so the table does not depend on the device the test runs on (it needs a real or emulated device for true font metrics). The screen is composed in a box of exactly
 * that size and the vertical scroll range of its container must be zero.
 */
@RunWith(Parameterized::class)
class HomeScreenFitTest(private val phone: Phone) {
    @get:Rule val compose = createComposeRule()

    data class Phone(val name: String, val widthDp: Int, val heightDp: Int) {
        override fun toString() = name
    }

    companion object {
        /** Phones at least this tall must show the whole home screen; shorter ones may scroll. */
        const val MUST_FIT_FROM_HEIGHT_DP = 800

        /** Smallest to largest phones in use; heights already exclude system bars. */
        @JvmStatic @Parameters(name = "{0}")
        fun phones(): List<Phone> = listOf(
        Phone("compact 360x592", 360, 592),
        Phone("Galaxy A-series 360x740", 360, 740),
        Phone("Pixel 4a 393x738", 393, 738),
        Phone("this phone 432x880", 432, 880),
        Phone("Pixel 8 Pro 448x890", 448, 890),
        Phone("Galaxy S-Ultra 412x860", 412, 860),
        Phone("tall 20:9 360x800", 360, 800),
        )
    }

    /** August 2026 starts on a Saturday: the worst case of six calendar rows. */
    private val sixWeekMonth = YearMonth.of(2026, 8)
    private val today = LocalDate.of(2026, 8, 18)

    private val modes = GameMode.entries.map { ModeOverview(it, 318, QueueStats(13, 4, 300)) }

    @Test fun wholeHomeScreenFitsWithoutScrolling() {
        val fontScale = 1f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(phone.widthDp.dp, phone.heightDp.dp)) {
                        ModeSelectScreen(
                            modes = modes,
                            daily = DailyProgress(answered = 15, goal = 50),
                            calendar = buildCalendar(emptyMap(), sixWeekMonth, today),
                            onPick = {}, onSettings = {}, onPreviousMonth = {}, onNextMonth = {},
                        )
                    }
                }
            }
        }
        val overflow = compose.onAllNodes(SemanticsMatcher("vertical scroll container") {
            it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
        }).fetchSemanticsNodes().maxOf {
            it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        }
        // Readable text wins over fitting: only phones tall enough for the normal-size layout must fit.
        if (phone.heightDp >= MUST_FIT_FROM_HEIGHT_DP) {
            assertEquals("px of home content hidden below the fold on ${phone.name}", 0f, overflow, 0f)
        }
    }
}

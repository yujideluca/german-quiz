package dev.yuji.quiz.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * How much a screen's structures (calendar cells, cards, question text, answer buttons) are enlarged or
 * shrunk for the phone they run on. Both factors follow the available height, so the design stays
 * proportional, and are capped so nothing becomes unreadably small or absurdly large. Readability beats
 * fitting: text never drops below its normal size, and a short screen scrolls instead.
 */
data class ScreenScale(val layout: Float, val text: Float) {
    fun dp(base: Dp): Dp = base * layout
    fun style(base: TextStyle): TextStyle = base.copy(fontSize = base.fontSize * text, lineHeight = base.lineHeight * text)

    companion object {
        /** Safe-area height (dp) at which every factor is 1.0: the designer's phone. */
        const val REFERENCE_HEIGHT_DP = 880f
        const val MIN_LAYOUT = 0.8f
        const val MAX_LAYOUT = 1.2f
        const val MIN_TEXT = 1f
        const val MAX_TEXT = 1.15f

        fun forHeight(heightDp: Float): ScreenScale {
            val ratio = heightDp / REFERENCE_HEIGHT_DP
            return ScreenScale(ratio.coerceIn(MIN_LAYOUT, MAX_LAYOUT), ratio.coerceIn(MIN_TEXT, MAX_TEXT))
        }
    }
}

val LocalScreenScale = compositionLocalOf { ScreenScale(1f, 1f) }

/** Measures the space a screen gets and provides the matching [ScreenScale] to everything inside it. */
@Composable
fun ScaledScreen(content: @Composable (ScreenScale) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = ScreenScale.forHeight(maxHeight.value)
        CompositionLocalProvider(LocalScreenScale provides scale) { content(scale) }
    }
}

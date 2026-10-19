package dev.yuji.quiz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.domain.ActivityCalendar
import dev.yuji.quiz.domain.CalendarDay
import dev.yuji.quiz.domain.HEAT_FULL_ANSWERS
import dev.yuji.quiz.domain.heatColorRgb
import java.time.format.TextStyle
import java.util.Locale

private val WeekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

/** Warm-spectrum colour of a trained day. Text on top is always dark: every colour in the ramp is light enough. */
private fun heat(answered: Int) = Color(0xFF000000.toInt() or heatColorRgb(answered))

/** Compact month calendar for the top of the home screen; also hosts the Settings entry. */
@Composable
fun ActivityCalendarCard(
    calendar: ActivityCalendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSettings: () -> Unit,
    compact: Boolean = false,
) {
    val scale = LocalScreenScale.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = scale.dp(12.dp), vertical = scale.dp(6.dp)), verticalArrangement = Arrangement.spacedBy(scale.dp(4.dp))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPreviousMonth) { Text("‹") }
                Text(
                    "${calendar.month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${calendar.month.year}",
                    style = scale.style(MaterialTheme.typography.titleMedium),
                )
                TextButton(onClick = onNextMonth, enabled = calendar.canGoNext) { Text("›") }
                Box(Modifier.weight(1f))
                TextButton(onClick = onSettings) { Text("Settings") }
            }
            Text(
                "Streak ${calendar.currentStreak} · longest ${calendar.longestStreak} · " +
                    "${calendar.trainedDaysInMonth} ${if (calendar.trainedDaysInMonth == 1) "day" else "days"} this month",
                style = scale.style(MaterialTheme.typography.labelMedium),
            )
            Row(Modifier.fillMaxWidth()) {
                WeekdayLetters.forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = scale.style(MaterialTheme.typography.labelSmall))
                }
            }
            val cells: List<CalendarDay?> = List(calendar.leadingBlanks) { null } + calendar.days
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(scale.dp(3.dp))) {
                    week.forEach { day -> DayCell(day, Modifier.weight(1f), scale.dp(CELL_HEIGHT)) }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }
            if (!compact) Legend()
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay?, modifier: Modifier, height: Dp) {
    val scale = LocalScreenScale.current
    val cell = modifier.height(height)
    if (day == null) {
        Box(cell)
        return
    }
    val shape = RoundedCornerShape(scale.dp(6.dp))
    val trained = day.answered > 0
    val background = if (trained) heat(day.answered)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (day.isFuture) 0.35f else 0.8f)
    val numberColor = if (trained) Color(0xFF2B1400) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        cell.clip(shape).background(background).let {
            if (day.isToday) it.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else it
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("${day.date.dayOfMonth}", color = numberColor, style = scale.style(MaterialTheme.typography.labelMedium))
    }
}

@Composable
private fun Legend() {
    val scale = LocalScreenScale.current
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("1", style = scale.style(MaterialTheme.typography.labelSmall))
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(
                Brush.horizontalGradient(listOf(heat(1), heat(HEAT_FULL_ANSWERS / 2), heat(HEAT_FULL_ANSWERS)))
            )
        )
        Text("$HEAT_FULL_ANSWERS+ answers", style = scale.style(MaterialTheme.typography.labelSmall))
    }
}

private val CELL_HEIGHT = 28.dp

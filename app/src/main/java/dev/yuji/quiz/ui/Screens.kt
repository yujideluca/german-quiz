package dev.yuji.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.ModeOverview
import dev.yuji.quiz.domain.ActivityCalendar
import dev.yuji.quiz.domain.AnswerRecord
import dev.yuji.quiz.domain.DailyProgress
import dev.yuji.quiz.domain.StudySession
import dev.yuji.quiz.domain.SuspensionDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CorrectGreen = Color(0xFF2E7D32)

@Composable
fun ModeSelectScreen(
    modes: List<ModeOverview>,
    daily: DailyProgress,
    calendar: ActivityCalendar,
    onPick: (ModeOverview) -> Unit,
    onSettings: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Short screens drop the optional detail so the whole home screen still fits without scrolling.
    val compact = maxHeight < COMPACT_HOME_HEIGHT
    ScaledScreen { scale ->
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(scale.dp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(scale.dp(8.dp)),
    ) {
        ActivityCalendarCard(calendar, onPreviousMonth, onNextMonth, onSettings, compact)
        DailyGoalCard(daily)
        modes.forEach { m ->
            val due = m.stats.learningDue + m.stats.reviewsDue + m.stats.newAvailable
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = scale.dp(16.dp), top = scale.dp(8.dp), end = scale.dp(8.dp), bottom = scale.dp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(scale.dp(8.dp)),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(scale.dp(2.dp))) {
                        Text(m.mode.title, style = scale.style(MaterialTheme.typography.titleMedium))
                        if (!compact) Text(m.mode.instruction, style = scale.style(MaterialTheme.typography.bodySmall))
                        Text(
                            "New ${m.stats.newAvailable} · Learning ${m.stats.learningDue} · " +
                                "Review ${m.stats.reviewsDue} · Deck ${m.totalCards}",
                            style = scale.style(MaterialTheme.typography.labelMedium),
                        )
                    }
                    Button(onClick = { onPick(m) }, enabled = due > 0) {
                        Text(if (due > 0) "Study ($due)" else "Done", style = scale.style(MaterialTheme.typography.labelLarge))
                    }
                }
            }
        }
    }
    }
    }
}

/** Safe-area height below which the home screen switches to its compact form. */
private val COMPACT_HOME_HEIGHT = 820.dp

@Composable
private fun DailyGoalCard(daily: DailyProgress) {
    val scale = LocalScreenScale.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = scale.dp(16.dp), vertical = scale.dp(10.dp)), verticalArrangement = Arrangement.spacedBy(scale.dp(6.dp))) {
            Text(
                if (daily.reached) "Daily goal reached ✓  ${daily.answered} / ${daily.goal}"
                else "Today: ${daily.answered} / ${daily.goal} questions",
                style = scale.style(MaterialTheme.typography.titleMedium),
            )
            LinearProgressIndicator(progress = { daily.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun StudyScreen(
    session: StudySession,
    choices: List<String>,
    daily: DailyProgress,
    showDebug: Boolean,
    onChoose: (String) -> Unit,
    onSuspend: (SuspensionDuration) -> Unit,
    onQuit: () -> Unit,
) {
    val card = session.currentCard ?: return
    val answer = session.lastAnswer
    ScaledScreen { scale ->
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(scale.dp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(scale.dp(12.dp)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("${session.mode.title} · ${session.queue.size} left · today ${daily.answered}/${daily.goal}", style = scale.style(MaterialTheme.typography.labelLarge))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onQuit) { Text("Quit", style = scale.style(MaterialTheme.typography.labelLarge)) }
                CardMenu(enabled = answer == null, onSuspend = onSuspend)
            }
        }
        Text(card.prompt, style = scale.style(MaterialTheme.typography.displaySmall))
        card.hint?.let {
            Text("Hint: $it", style = scale.style(MaterialTheme.typography.titleMedium), fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary)
        }
        card.context?.let { Text(it, style = scale.style(MaterialTheme.typography.headlineSmall)) }
        choices.forEach { option ->
            val (container, content) = when {
                answer == null -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                option == card.correctChoice -> CorrectGreen to Color.White
                option == answer.chosen -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            // Disabled colours are set explicitly, otherwise Material greys out every answered button.
            val colors = ButtonDefaults.buttonColors(
                containerColor = container, contentColor = content,
                disabledContainerColor = container, disabledContentColor = content,
            )
            Button(
                onClick = { onChoose(option) }, colors = colors, enabled = answer == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = scale.dp(68.dp)),
            ) { Text(option, style = scale.style(MaterialTheme.typography.headlineSmall)) }
        }
        if (answer != null) {
            Text(card.explanation, style = scale.style(MaterialTheme.typography.bodyLarge))
            if (showDebug) DebugOverlay(card.id, answer)
        }
    }
    }
}

/** The "⋮" button in the top-right corner of a question. */
@Composable
private fun CardMenu(enabled: Boolean, onSuspend: (SuspensionDuration) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, enabled = enabled) {
        Text("⋮", style = MaterialTheme.typography.headlineSmall)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Remove for 1 week") },
            onClick = { open = false; onSuspend(SuspensionDuration.ONE_WEEK) })
        DropdownMenuItem(text = { Text("Remove for 1 month") },
            onClick = { open = false; onSuspend(SuspensionDuration.ONE_MONTH) })
        DropdownMenuItem(text = { Text("Remove permanently") },
            onClick = { open = false; onSuspend(SuspensionDuration.PERMANENT) })
    }
}

@Composable
private fun DebugOverlay(cardId: String, a: AnswerRecord) {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Text(
        "debug · $cardId\n" +
            "grade=${a.grade} ${a.responseMillis}ms\n" +
            "${a.before.phase} → ${a.after.phase}  step=${a.after.learningStep}\n" +
            "ease=${"%.2f".format(a.after.easeFactor)} interval=${a.after.intervalDays}d " +
            "lapses=${a.after.lapses} reps=${a.after.repetitions}\n" +
            "due=${fmt.format(Date(a.after.dueAtMillis))}",
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun FinishedScreen(session: StudySession, onBack: () -> Unit) = ScaledScreen { scale ->
    Column(
        Modifier.fillMaxSize().padding(scale.dp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(scale.dp(16.dp), Alignment.CenterVertically),
    ) {
        Text("Session complete", style = scale.style(MaterialTheme.typography.headlineLarge))
        Text("${session.correctCount} correct · ${session.wrongCount} wrong",
            style = scale.style(MaterialTheme.typography.titleLarge))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to modes") }
    }
}

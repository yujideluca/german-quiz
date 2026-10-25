package dev.yuji.quiz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.R
import dev.yuji.quiz.domain.Card as StudyCard
import dev.yuji.quiz.domain.GameMode
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
                        Text(stringResource(m.mode.titleRes()), style = scale.style(MaterialTheme.typography.titleMedium))
                        if (!compact) Text(stringResource(m.mode.instructionRes()), style = scale.style(MaterialTheme.typography.bodySmall))
                        Text(
                            stringResource(R.string.home_stats, m.stats.newAvailable, m.stats.learningDue,
                                m.stats.reviewsDue, m.totalCards),
                            style = scale.style(MaterialTheme.typography.labelMedium),
                        )
                    }
                    Button(onClick = { onPick(m) }, enabled = due > 0) {
                        Text(if (due > 0) stringResource(R.string.home_study, due) else stringResource(R.string.common_done), style = scale.style(MaterialTheme.typography.labelLarge))
                    }
                }
            }
        }
    }
    }
    }
}

/** Safe-area height below which the home screen switches to its compact form. */
private val COMPACT_HOME_HEIGHT = 900.dp

@Composable
private fun DailyGoalCard(daily: DailyProgress) {
    val scale = LocalScreenScale.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = scale.dp(16.dp), vertical = scale.dp(10.dp)), verticalArrangement = Arrangement.spacedBy(scale.dp(6.dp))) {
            Text(
                if (daily.reached) stringResource(R.string.daily_reached, daily.answered, daily.goal)
                else stringResource(R.string.daily_progress, daily.answered, daily.goal),
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
    onNext: () -> Unit,
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
            Text(stringResource(R.string.study_header, stringResource(session.mode.titleRes()), session.queue.size, daily.answered, daily.goal), style = scale.style(MaterialTheme.typography.labelLarge))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onQuit) { Text(stringResource(R.string.study_quit), style = scale.style(MaterialTheme.typography.labelLarge)) }
                CardMenu(onSuspend = onSuspend)
            }
        }
        if (card.mode == GameMode.ERROR_SPOT) {
            ErrorSpotBody(card, choices, answer?.chosen, onChoose)
        } else {
        Text(card.prompt, style = scale.style(MaterialTheme.typography.displaySmall))
        card.hint?.let {
            Text(stringResource(R.string.study_hint, LocalContentTranslations.current.hint(it)), style = scale.style(MaterialTheme.typography.titleMedium), fontStyle = FontStyle.Italic,
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
        }
        if (answer != null) {
            Text(LocalContentTranslations.current.explanation(card.explanation), style = scale.style(MaterialTheme.typography.bodyLarge))
            if (!card.mode.autoAdvances) {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = scale.dp(56.dp))) {
                    Text(stringResource(R.string.study_next), style = scale.style(MaterialTheme.typography.titleLarge))
                }
            }
            if (showDebug) DebugOverlay(card.id, answer)
        }
    }
    }
}

/**
 * "Fehler finden": the sentence is laid out as tappable chunks. After answering, the wrong chunk is green
 * (the right answer) and a wrong tap is red, the same colours the multiple-choice buttons use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorSpotBody(card: StudyCard, chunks: List<String>, chosen: String?, onChoose: (String) -> Unit) {
    val scale = LocalScreenScale.current
    Text(card.prompt, style = scale.style(MaterialTheme.typography.headlineMedium))
    Text(stringResource(R.string.errors_tap_wrong), style = scale.style(MaterialTheme.typography.titleMedium), fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.primary)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(scale.dp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(scale.dp(8.dp)),
    ) {
        chunks.forEach { chunk ->
            val (container, content) = when {
                chosen == null -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                chunk == card.correctChoice -> CorrectGreen to Color.White
                chunk == chosen -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
                else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                onClick = { onChoose(chunk) }, enabled = chosen == null,
                color = container, contentColor = content, shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(chunk, style = scale.style(MaterialTheme.typography.titleLarge),
                    modifier = Modifier.padding(horizontal = scale.dp(12.dp), vertical = scale.dp(10.dp)))
            }
        }
    }
}

/** The "⋮" button in the top-right corner of a question. */
@Composable
private fun CardMenu(onSuspend: (SuspensionDuration) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text("⋮", style = MaterialTheme.typography.headlineSmall)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.card_remove_week)) },
            onClick = { open = false; onSuspend(SuspensionDuration.ONE_WEEK) })
        DropdownMenuItem(text = { Text(stringResource(R.string.card_remove_month)) },
            onClick = { open = false; onSuspend(SuspensionDuration.ONE_MONTH) })
        DropdownMenuItem(text = { Text(stringResource(R.string.card_remove_permanent)) },
            onClick = { open = false; onSuspend(SuspensionDuration.PERMANENT) })
    }
}

@Composable
private fun DebugOverlay(cardId: String, a: AnswerRecord) {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)
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
        Text(stringResource(R.string.finished_title), style = scale.style(MaterialTheme.typography.headlineLarge))
        Text(stringResource(R.string.finished_score, session.correctCount, session.wrongCount),
            style = scale.style(MaterialTheme.typography.titleLarge))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finished_back)) }
    }
}

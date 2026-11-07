package dev.yuji.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.R
import dev.yuji.quiz.content.FspCaseRun
import dev.yuji.quiz.content.FspGrading
import dev.yuji.quiz.domain.AnswerRecord
import kotlin.math.roundToInt

private val RightGreen = Color(0xFF2E7D32)

private fun partTitle(part: Int) = when (part) {
    1 -> R.string.fsp_part_1
    2 -> R.string.fsp_part_2
    else -> R.string.fsp_part_3
}

/**
 * One simulated FSP case (ADR-0014): the steps of the three exam parts one after the other, read like a script (FS-6):
 * the player's last line, then what the patient or the Oberarzt answers and does, then the four things the player
 * could say or write next. A wrong pick turns red and shows why; the player picks again until the right answer (FS-3).
 * After the last step the case is graded and the result screen shows the score per part and the diagnosis.
 */
@Composable
fun FspCaseBody(run: FspCaseRun, answer: AnswerRecord?, onPick: (Int) -> Unit, onNextStep: () -> Unit, onNextCard: () -> Unit) {
    val scale = LocalScreenScale.current
    val case = run.case
    Text(case.area, style = scale.style(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.primary)
    Text(case.title, style = scale.style(MaterialTheme.typography.headlineSmall))
    if (run.isFinished) {
        FspResult(run, answer, onNextCard)
        return
    }
    val step = run.step ?: return
    if (run.stepIndex == 0) Text(case.patient, style = scale.style(MaterialTheme.typography.bodyLarge), fontStyle = FontStyle.Italic)
    Text(stringResource(partTitle(step.part)) + "  ·  " + step.section, style = scale.style(MaterialTheme.typography.titleSmall))
    LinearProgressIndicator(progress = { run.stepIndex.toFloat() / case.steps.size }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.fsp_step, run.stepIndex + 1, case.steps.size), style = scale.style(MaterialTheme.typography.labelMedium))
    // The script so far: the line the player chose last, as long as the same conversation goes on.
    case.steps.getOrNull(run.stepIndex - 1)?.takeIf { it.part == step.part && it.part != 2 }?.let { prev ->
        Text(stringResource(R.string.fsp_you_said, prev.options[prev.answer]), style = scale.style(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(step.situation, style = scale.style(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.SemiBold)
    run.order().forEach { i ->
        val wrong = i in run.wrongPicks
        val right = run.solved && i == step.answer
        val (container, content) = when {
            right -> RightGreen to Color.White
            wrong -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
            run.solved -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        }
        val colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container, disabledContentColor = content,
        )
        Button(
            // Rounded rectangle, not the default pill: answers run to several lines and a pill clips their corners.
            onClick = { onPick(i) }, colors = colors, enabled = !run.solved && !wrong, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = scale.dp(56.dp)),
        ) { Text(step.options[i], style = scale.style(MaterialTheme.typography.bodyLarge), textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
        if (wrong || right) {
            Text(step.feedback[i], style = scale.style(MaterialTheme.typography.bodyMedium),
                color = if (right) RightGreen else MaterialTheme.colorScheme.error)
        }
    }
    if (!run.solved && run.wrongPicks.isNotEmpty()) {
        Text(stringResource(R.string.fsp_try_again), style = scale.style(MaterialTheme.typography.bodyMedium), fontStyle = FontStyle.Italic)
    }
    if (run.solved) {
        step.reply?.let { Text(it, style = scale.style(MaterialTheme.typography.bodyLarge), fontStyle = FontStyle.Italic) }
        Button(onClick = onNextStep, modifier = Modifier.fillMaxWidth().heightIn(min = scale.dp(56.dp))) {
            Text(stringResource(R.string.fsp_continue), style = scale.style(MaterialTheme.typography.titleLarge))
        }
    }
}

@Composable
private fun FspResult(run: FspCaseRun, answer: AnswerRecord?, onNextCard: () -> Unit) {
    val scale = LocalScreenScale.current
    val percent = (run.score * 100).roundToInt()
    val passed = FspGrading.passed(run.score)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(scale.dp(16.dp)), verticalArrangement = Arrangement.spacedBy(scale.dp(8.dp))) {
            Text(stringResource(R.string.fsp_result_title), style = scale.style(MaterialTheme.typography.titleLarge))
            Text(
                stringResource(if (passed) R.string.fsp_result_passed else R.string.fsp_result_failed, percent),
                style = scale.style(MaterialTheme.typography.titleMedium),
                color = if (passed) RightGreen else MaterialTheme.colorScheme.error,
            )
            run.partScores().forEach { (part, score) ->
                Text(stringResource(R.string.fsp_result_part, stringResource(partTitle(part)), score.first, score.second),
                    style = scale.style(MaterialTheme.typography.bodyLarge))
            }
            Text(stringResource(R.string.fsp_diagnosis, run.case.diagnosis), style = scale.style(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.SemiBold)
        }
    }
    if (answer != null) {
        Button(onClick = onNextCard, modifier = Modifier.fillMaxWidth().heightIn(min = scale.dp(56.dp))) {
            Text(stringResource(R.string.study_next), style = scale.style(MaterialTheme.typography.titleLarge))
        }
    }
}

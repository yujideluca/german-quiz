package dev.yuji.quiz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.data.ThemeMode
import dev.yuji.quiz.domain.CardStatus
import dev.yuji.quiz.domain.ManagedCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Confirm(val title: String, val body: String) {
    SUSPENDED("Reset suspended cards?", "All suspended questions return to the pool."),
    REMOVED("Reset removed cards?", "All permanently removed questions return to the pool."),
    ALL_PROGRESS("Reset all progress?", "This erases every learning state, suspension and removal. It cannot be undone."),
}

@Composable
fun SettingsScreen(
    state: UiState.Settings,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onQuery: (String) -> Unit,
    onRestoreCard: (String) -> Unit,
    onRestoreSuspended: () -> Unit,
    onRestoreRemoved: () -> Unit,
    onResetAll: () -> Unit,
) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showRemoved by rememberSaveable { mutableStateOf(false) }

    if (showRemoved) {
        BackHandler { showRemoved = false }
        RemovedQuestionsScreen(
            state, { showRemoved = false }, onQuery, onRestoreCard,
            onResetSuspended = { confirm = Confirm.SUSPENDED }, onResetRemoved = { confirm = Confirm.REMOVED },
        )
    } else Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("Done") }
        }

        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        listOf(
            ThemeMode.SYSTEM to "Use system default",
            ThemeMode.LIGHT to "Light mode",
            ThemeMode.DARK to "Night mode",
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = state.themeMode == mode, role = Role.RadioButton,
                    onClick = { onTheme(mode) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.themeMode == mode, onClick = null, modifier = Modifier.padding(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider()
        Text("Removed questions", style = MaterialTheme.typography.titleMedium)
        Text("Suspended: ${state.counts.suspended}  ·  Removed: ${state.counts.removed}",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { showRemoved = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Manage suspended and removed questions")
        }

        HorizontalDivider()
        Text("Progress", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { confirm = Confirm.ALL_PROGRESS }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset all progress")
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title) },
            text = { Text(action.body) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        Confirm.SUSPENDED -> onRestoreSuspended()
                        Confirm.REMOVED -> onRestoreRemoved()
                        Confirm.ALL_PROGRESS -> onResetAll()
                    }
                    confirm = null
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RemovedQuestionsScreen(
    state: UiState.Settings,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onRestoreCard: (String) -> Unit,
    onResetSuspended: () -> Unit,
    onResetRemoved: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Removed questions", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Suspended: ${state.counts.suspended}  ·  Removed: ${state.counts.removed}",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onResetSuspended, enabled = state.counts.suspended > 0,
            modifier = Modifier.fillMaxWidth()) { Text("Reset all suspended cards") }
        OutlinedButton(onClick = onResetRemoved, enabled = state.counts.removed > 0,
            modifier = Modifier.fillMaxWidth()) { Text("Reset all removed cards") }

        OutlinedTextField(
            value = state.query, onValueChange = onQuery, singleLine = true,
            label = { Text("Search suspended or removed cards") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.results.isEmpty()) {
            Text(
                if (state.counts.suspended + state.counts.removed == 0) "No suspended or removed cards."
                else "No card matches “${state.query}”.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.results.take(MAX_RESULTS).forEach { managed -> ManagedCardRow(managed, onRestoreCard) }
        if (state.results.size > MAX_RESULTS) {
            Text("Showing $MAX_RESULTS of ${state.results.size}. Refine the search to see more.",
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ManagedCardRow(managed: ManagedCard, onRestore: (String) -> Unit) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val status = when (managed.status) {
        CardStatus.REMOVED -> "Removed"
        CardStatus.SUSPENDED -> "Suspended until ${fmt.format(Date(managed.suspendedUntilMillis))}"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(managed.card.prompt, style = MaterialTheme.typography.titleMedium)
                managed.card.context?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("${managed.card.mode.title} · $status", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { onRestore(managed.card.id) }) { Text("Restore") }
        }
    }
}

private const val MAX_RESULTS = 40

package dev.yuji.quiz.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.BuildConfig
import dev.yuji.quiz.R
import dev.yuji.quiz.data.AppLanguage
import dev.yuji.quiz.data.ThemeMode
import dev.yuji.quiz.domain.CardStatus
import dev.yuji.quiz.domain.DAILY_GOAL_STEP
import dev.yuji.quiz.domain.MAX_DAILY_GOAL
import dev.yuji.quiz.domain.MIN_DAILY_GOAL
import dev.yuji.quiz.domain.ManagedCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Confirm(@StringRes val title: Int, @StringRes val body: Int) {
    SUSPENDED(R.string.confirm_suspended_title, R.string.confirm_suspended_body),
    REMOVED(R.string.confirm_removed_title, R.string.confirm_removed_body),
    ALL_PROGRESS(R.string.confirm_all_title, R.string.confirm_all_body),
}

private fun languageNameRes(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.use_system_default
    AppLanguage.ENGLISH -> R.string.language_name_english
    AppLanguage.GERMAN -> R.string.language_name_german
    AppLanguage.PORTUGUESE -> R.string.language_name_portuguese
    AppLanguage.SPANISH -> R.string.language_name_spanish
}

@Composable
fun SettingsScreen(
    state: UiState.Settings,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onDailyGoal: (Int) -> Unit,
    onQuery: (String) -> Unit,
    onRestoreCard: (String) -> Unit,
    onRestoreSuspended: () -> Unit,
    onRestoreRemoved: () -> Unit,
    onResetAll: () -> Unit,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: (dev.yuji.quiz.domain.UpdateManifest) -> Unit,
    backup: BackupUi,
    backupActions: BackupActions,
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
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
        }

        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
        listOf(
            ThemeMode.SYSTEM to R.string.use_system_default,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = state.themeMode == mode, role = Role.RadioButton,
                    onClick = { onTheme(mode) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.themeMode == mode, onClick = null, modifier = Modifier.padding(8.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        AppLanguage.entries.forEach { language ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = state.language == language, role = Role.RadioButton,
                    onClick = { onLanguage(language) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.language == language, onClick = null, modifier = Modifier.padding(8.dp))
                Text(stringResource(languageNameRes(language)), style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_daily_goal), style = MaterialTheme.typography.titleMedium)
        var goal by remember(state.dailyGoal) { mutableStateOf(state.dailyGoal.toFloat()) }
        Text(stringResource(R.string.settings_daily_goal_value, goal.toInt()), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = goal, onValueChange = { goal = it }, onValueChangeFinished = { onDailyGoal(goal.toInt()) },
            valueRange = MIN_DAILY_GOAL.toFloat()..MAX_DAILY_GOAL.toFloat(),
            steps = (MAX_DAILY_GOAL - MIN_DAILY_GOAL) / DAILY_GOAL_STEP - 1,
        )

        HorizontalDivider()
        Text(stringResource(R.string.settings_removed_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_removed_counts, state.counts.suspended, state.counts.removed),
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { showRemoved = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_manage_removed))
        }

        HorizontalDivider()
        BackupSection(backup, backupActions)

        HorizontalDivider()
        Text(stringResource(R.string.settings_progress), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { confirm = Confirm.ALL_PROGRESS }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_reset_all))
        }

        HorizontalDivider()
        UpdateSection(update, onCheckUpdate, onInstallUpdate)
        Text(
            stringResource(R.string.pool_version, state.poolVersion,
                stringResource(if (state.poolDownloaded) R.string.pool_downloaded else R.string.pool_built_in)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(action.title)) },
            text = { Text(stringResource(action.body)) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        Confirm.SUSPENDED -> onRestoreSuspended()
                        Confirm.REMOVED -> onRestoreRemoved()
                        Confirm.ALL_PROGRESS -> onResetAll()
                    }
                    confirm = null
                }) { Text(stringResource(R.string.common_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.common_cancel)) } },
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
            Text(stringResource(R.string.settings_removed_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        }
        Text(stringResource(R.string.settings_removed_counts, state.counts.suspended, state.counts.removed),
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onResetSuspended, enabled = state.counts.suspended > 0,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.removed_reset_suspended)) }
        OutlinedButton(onClick = onResetRemoved, enabled = state.counts.removed > 0,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.removed_reset_removed)) }

        OutlinedTextField(
            value = state.query, onValueChange = onQuery, singleLine = true,
            label = { Text(stringResource(R.string.removed_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.results.isEmpty()) {
            Text(
                if (state.counts.suspended + state.counts.removed == 0) stringResource(R.string.removed_none)
                else stringResource(R.string.removed_no_match, state.query),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.results.take(MAX_RESULTS).forEach { managed -> ManagedCardRow(managed, onRestoreCard) }
        if (state.results.size > MAX_RESULTS) {
            Text(stringResource(R.string.removed_showing, MAX_RESULTS, state.results.size),
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ManagedCardRow(managed: ManagedCard, onRestore: (String) -> Unit) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val status = when (managed.status) {
        CardStatus.REMOVED -> stringResource(R.string.card_status_removed)
        CardStatus.SUSPENDED -> stringResource(R.string.card_status_suspended, fmt.format(Date(managed.suspendedUntilMillis)))
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(managed.card.prompt, style = MaterialTheme.typography.titleMedium)
                managed.card.context?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("${stringResource(managed.card.mode.titleRes())} · $status", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { onRestore(managed.card.id) }) { Text(stringResource(R.string.common_restore)) }
        }
    }
}

private const val MAX_RESULTS = 40

@Composable
private fun UpdateSection(
    update: UpdateState,
    onCheck: () -> Unit,
    onInstall: (dev.yuji.quiz.domain.UpdateManifest) -> Unit,
) {
    Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.update_installed, BuildConfig.VERSION_NAME, BuildConfig.CHANNEL_LABEL), style = MaterialTheme.typography.bodyMedium)
    val status = when (update) {
        UpdateState.Idle -> null
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Available -> stringResource(R.string.update_available, update.manifest.versionName)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, update.manifest.versionName)
        is UpdateState.NeedsPermission -> stringResource(R.string.update_needs_permission)
        is UpdateState.DownloadFailed -> stringResource(R.string.update_download_failed, update.message)
        is UpdateState.Failed -> stringResource(R.string.update_failed, update.message)
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    val busy = update is UpdateState.Checking || update is UpdateState.Downloading
    val pending = (update as? UpdateState.Available)?.manifest ?: (update as? UpdateState.NeedsPermission)?.manifest
        ?: (update as? UpdateState.DownloadFailed)?.manifest
    if (pending != null) {
        Button(onClick = { onInstall(pending) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.update_button_to, pending.versionName))
        }
    }
    OutlinedButton(onClick = onCheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_check))
    }
}

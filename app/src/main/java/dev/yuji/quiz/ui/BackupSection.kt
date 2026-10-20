package dev.yuji.quiz.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.yuji.quiz.R
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.DateFormat
import java.util.Date

private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

data class BackupSummary(val cards: Int, val days: Int, val exportedAtMillis: Long)

data class BackupUi(val notice: String? = null, val pending: BackupSummary? = null)

class BackupActions(
    val fileName: () -> String,
    val exportText: () -> String?,
    val read: (String) -> Unit,
    val apply: () -> Unit,
    val discard: () -> Unit,
    val notice: (String) -> Unit,
)

/** Export / import of the study progress through Android's file picker (local storage, Google Drive, ...). */
@Composable
fun BackupSection(ui: BackupUi, actions: BackupActions) {
    val context = LocalContext.current
    val nothingToExport = stringResource(R.string.backup_nothing)
    val exported = stringResource(R.string.backup_exported)
    val exportFailed = stringResource(R.string.backup_export_failed, "%s")
    val readFailed = stringResource(R.string.backup_read_failed, "%s")
    val tooLarge = stringResource(R.string.backup_too_large)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = actions.exportText() ?: return@rememberLauncherForActivityResult actions.notice(nothingToExport)
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        }.onSuccess { actions.notice(exported) }
            .onFailure { actions.notice(exportFailed.format(it.message)) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)!!.use { readLimited(it, tooLarge) } }
            .onSuccess(actions.read)
            .onFailure { actions.notice(readFailed.format(it.message)) }
    }

    Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.backup_explain),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { exportLauncher.launch(actions.fileName()) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_export))
    }
    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_import))
    }
    ui.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

    ui.pending?.let { found ->
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(found.exportedAtMillis))
        AlertDialog(
            onDismissRequest = actions.discard,
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Text(stringResource(R.string.backup_restore_text, date, found.cards, found.days))
            },
            confirmButton = { TextButton(onClick = actions.apply) { Text(stringResource(R.string.common_restore)) } },
            dismissButton = { TextButton(onClick = actions.discard) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private fun readLimited(input: InputStream, tooLargeMessage: String): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > MAX_BACKUP_BYTES) error(tooLargeMessage)
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}

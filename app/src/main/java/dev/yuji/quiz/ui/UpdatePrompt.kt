package dev.yuji.quiz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yuji.quiz.domain.UpdateManifest

/** What the update popup shows; null from [updatePromptFor] means no popup. */
sealed interface UpdatePrompt {
    val manifest: UpdateManifest
    data class Offer(override val manifest: UpdateManifest) : UpdatePrompt
    data class Downloading(override val manifest: UpdateManifest) : UpdatePrompt
    data class NeedsPermission(override val manifest: UpdateManifest) : UpdatePrompt
    data class DownloadFailed(override val manifest: UpdateManifest, val message: String) : UpdatePrompt
}

/** The popup appears on the home screen only (a study session is never interrupted) and only until "Later". */
fun updatePromptFor(update: UpdateState, dismissed: Boolean, onHomeScreen: Boolean): UpdatePrompt? {
    if (dismissed || !onHomeScreen) return null
    return when (update) {
        is UpdateState.Available -> UpdatePrompt.Offer(update.manifest)
        is UpdateState.Downloading -> UpdatePrompt.Downloading(update.manifest)
        is UpdateState.NeedsPermission -> UpdatePrompt.NeedsPermission(update.manifest)
        is UpdateState.DownloadFailed -> UpdatePrompt.DownloadFailed(update.manifest, update.message)
        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate, is UpdateState.Failed -> null
    }
}

@Composable
fun UpdatePromptDialog(prompt: UpdatePrompt, onUpdate: (UpdateManifest) -> Unit, onLater: () -> Unit) {
    val version = prompt.manifest.versionName
    val update = @Composable { label: String -> TextButton(onClick = { onUpdate(prompt.manifest) }) { Text(label) } }
    val later = @Composable { TextButton(onClick = onLater) { Text("Later") } }
    when (prompt) {
        is UpdatePrompt.Offer -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("Update available") },
            text = { Text("Version $version is ready on GitHub. Your progress is kept.") },
            confirmButton = { update("Update") }, dismissButton = later,
        )
        is UpdatePrompt.Downloading -> AlertDialog(
            onDismissRequest = {},                                  // seconds long; fails visibly on a timeout
            title = { Text("Updating…") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("Downloading version $version. Android will ask you to confirm the install.")
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            },
            confirmButton = {},
        )
        is UpdatePrompt.NeedsPermission -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("Allow updates") },
            text = {
                Text("Android needs your permission to install updates from this app. " +
                    "Allow it in the screen that opened, then tap Update again.")
            },
            confirmButton = { update("Update") }, dismissButton = later,
        )
        is UpdatePrompt.DownloadFailed -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("Update failed") },
            text = { Text("${prompt.message}. Your progress is safe.") },
            confirmButton = { update("Try again") }, dismissButton = later,
        )
    }
}

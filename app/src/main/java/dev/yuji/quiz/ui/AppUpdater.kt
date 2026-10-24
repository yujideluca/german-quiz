package dev.yuji.quiz.ui

import android.app.Application
import androidx.annotation.StringRes
import dev.yuji.quiz.R
import dev.yuji.quiz.android.ApkInstaller
import dev.yuji.quiz.BuildConfig
import dev.yuji.quiz.data.ActiveContent
import dev.yuji.quiz.data.UpdateClient
import dev.yuji.quiz.data.UpdateFailure
import dev.yuji.quiz.data.UpdateFailureKind
import dev.yuji.quiz.domain.UpdateManifest
import dev.yuji.quiz.domain.shouldCheckForUpdate
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "update from GitHub" flow of the view model: asking the feed, the popup that follows, and downloading the APK.
 * [text] is the view model's translator (the language chosen in Settings); [onPoolInstalled] hands a downloaded
 * question pool back to the view model, which decides when it may replace the deck.
 */
class AppUpdater(
    private val app: Application,
    private val updateClient: UpdateClient,
    private val pool: QuestionPool,
    private val scope: CoroutineScope,
    private val text: (Int, Array<out Any>) -> String,
    private val onPoolInstalled: (ActiveContent) -> Boolean,
) {
    private val updateFile = File(app.cacheDir, "updates/update.apk")
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    private var lastCheckAtMillis: Long? = null
    private val _promptDismissed = MutableStateFlow(false)
    /** True after "Later": the popup stays hidden until the app is opened again. */
    val promptDismissed: StateFlow<Boolean> = _promptDismissed

    /** Called every time the app comes to the foreground: asks the feed (at most every 30 s) and re-arms the popup. */
    fun onAppOpened() {
        _promptDismissed.value = false
        if (shouldCheckForUpdate(System.currentTimeMillis(), lastCheckAtMillis)) checkForUpdate()
    }

    fun dismissUpdatePrompt() { _promptDismissed.value = true }

    fun checkForUpdate() {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        lastCheckAtMillis = System.currentTimeMillis()
        _update.value = stateWhileChecking(_update.value)
        pool.check(onPoolInstalled)
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { updateClient.fetchManifest() } }
                .withUserMessage(R.string.update_check_failed_default)
            _update.value = stateAfterCheck(_update.value, result, BuildConfig.VERSION_CODE)
        }
    }

    /** Downloads the new APK and opens Android's installer; study progress lives in app data and survives. */
    fun installUpdate(manifest: UpdateManifest) {
        _update.value = UpdateState.Downloading(manifest)
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { updateClient.downloadApk(manifest, updateFile) } }
            _update.value = result.fold(
                onSuccess = {
                    if (ApkInstaller.install(app, updateFile)) UpdateState.Available(manifest)
                    else UpdateState.NeedsPermission(manifest)
                },
                onFailure = { UpdateState.DownloadFailed(manifest, it.userMessage(R.string.update_download_failed_default)) },
            )
        }
    }

    /** The failure's message in the chosen language; unknown failures keep their own text, or [fallback] when empty. */
    private fun Throwable.userMessage(@StringRes fallback: Int): String = when (this) {
        is UpdateFailure -> when (kind) {
            UpdateFailureKind.MALFORMED_FEED -> text(R.string.update_err_feed, emptyArray())
            UpdateFailureKind.CHECKSUM_MISMATCH -> text(R.string.update_err_checksum, emptyArray())
            UpdateFailureKind.RATE_LIMIT -> text(R.string.update_err_rate_limit, emptyArray())
            UpdateFailureKind.SERVER_ERROR -> text(R.string.update_err_server, arrayOf(httpCode))
        }
        else -> message ?: text(fallback, emptyArray())
    }

    private fun <T> Result<T>.withUserMessage(@StringRes fallback: Int): Result<T> =
        fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(IllegalStateException(it.userMessage(fallback), it)) })
}

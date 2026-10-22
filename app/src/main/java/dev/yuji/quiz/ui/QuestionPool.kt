package dev.yuji.quiz.ui

import android.app.Application
import dev.yuji.quiz.content.ContentFiles
import dev.yuji.quiz.content.poolGrowth
import dev.yuji.quiz.data.ActiveContent
import dev.yuji.quiz.data.AssetContentFiles
import dev.yuji.quiz.data.ContentFeed
import dev.yuji.quiz.data.ContentStore
import dev.yuji.quiz.data.ContentUpdate
import dev.yuji.quiz.data.ContentUpdater
import dev.yuji.quiz.data.resolveContent
import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.StudyLog
import dev.yuji.quiz.debug.debug
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The question pool in use and its updates from the feed, without a new APK (ADR-0012). A downloaded pack is kept in
 * `files/content`; the built-in one in the assets. Only the real app calls [load]; until then [check] does nothing,
 * so test view models with their own deck never touch the network.
 */
class QuestionPool(app: Application, feed: ContentFeed, private val log: StudyLog, private val scope: CoroutineScope) {
    private val store = ContentStore(File(app.filesDir, "content"))
    private val builtIn = AssetContentFiles(app)
    private val updater = ContentUpdater(feed, store)
    private var checking = false
    /** A pool that arrived while it could not be applied (during a study session). */
    private var pending: ActiveContent? = null

    var active: ActiveContent? = null
        private set

    private val _files = MutableStateFlow<ContentFiles>(builtIn)
    /** The files of the pool in use; the translations of its English text are read from here. */
    val files: StateFlow<ContentFiles> = _files

    private val _notice = MutableStateFlow<Int?>(null)
    /** Questions added by the last pool update, shown once on the home screen (QP-3); null when there is nothing to say. */
    val notice: StateFlow<Int?> = _notice

    fun dismissNotice() { _notice.value = null }

    fun load(): ActiveContent = resolveContent(builtIn, store, log).also { active = it; _files.value = it.files }

    /** Looks for a newer pack; when one is installed, [apply] gets it and returns false if it must wait for the home screen. */
    fun check(apply: (ActiveContent) -> Boolean) {
        val current = active ?: return
        if (checking) return
        checking = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { updater.update(pending?.version ?: current.version) } }
            checking = false
            when (val update = result.getOrNull()) {
                is ContentUpdate.Installed -> {
                    log.debug(LogChannel.CONTENT) { "question pool ${update.content.version} installed" }
                    if (!apply(update.content)) pending = update.content
                }
                is ContentUpdate.Refused -> log.debug(LogChannel.CONTENT) { "question pool refused: ${update.problem} ${update.detail}" }
                ContentUpdate.UpToDate -> Unit
                null -> log.debug(LogChannel.CONTENT) { "question pool check failed: ${result.exceptionOrNull()}" }
            }
        }
    }

    /** The pool waiting to be applied, if any; it is handed out once. */
    fun takePending(): ActiveContent? = pending.also { pending = null }

    /** Makes [next] the pool in use and announces how many questions it added. */
    fun activate(next: ActiveContent): ActiveContent {
        val before = active?.deck.orEmpty()
        active = next
        pending = null
        _files.value = next.files
        _notice.value = poolGrowth(before, next.deck)
        return next
    }
}

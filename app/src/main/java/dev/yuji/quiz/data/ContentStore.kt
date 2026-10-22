package dev.yuji.quiz.data

import android.content.Context
import dev.yuji.quiz.content.CONTENT_FILES
import dev.yuji.quiz.content.CONTENT_MANIFEST_FILE
import dev.yuji.quiz.content.ContentFiles
import dev.yuji.quiz.content.ContentManifest
import dev.yuji.quiz.content.buildDeckFrom
import dev.yuji.quiz.content.parseContentManifest
import dev.yuji.quiz.content.prefersDownloaded
import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.StudyLog
import dev.yuji.quiz.debug.debug
import dev.yuji.quiz.domain.Card
import dev.yuji.quiz.domain.GameMode
import java.io.File

/** The pack built into the APK (`assets/content.json`, `assets/vocab/`, `assets/i18n/`). */
class AssetContentFiles(private val context: Context) : ContentFiles {
    override fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
}

class DirContentFiles(private val dir: File) : ContentFiles {
    override fun read(path: String) = File(dir, path).readText()
}

/**
 * Keeps at most one downloaded pack in `[root]/current`. A new pack is written to `[root]/staging` in full
 * (manifest last) and only then swapped in, so an interrupted install leaves either the old pack or none
 * (the built-in pack is then used); never half of one.
 */
class ContentStore(root: File) {
    private val current = File(root, "current")
    private val staging = File(root, "staging")

    val files: ContentFiles get() = DirContentFiles(current)

    fun installedManifest(): ContentManifest? =
        File(current, CONTENT_MANIFEST_FILE).takeIf { it.isFile }?.let { runCatching { parseContentManifest(it.readText()) }.getOrNull() }

    fun install(manifestText: String, bytes: Map<String, ByteArray>) {
        staging.deleteRecursively()
        CONTENT_FILES.forEach { path -> File(staging, path).apply { parentFile?.mkdirs() }.writeBytes(bytes.getValue(path)) }
        File(staging, CONTENT_MANIFEST_FILE).writeText(manifestText)
        current.deleteRecursively()
        check(staging.renameTo(current)) { "could not move the new question pool into place" }
    }

    fun clear() {
        current.deleteRecursively()
        staging.deleteRecursively()
    }
}

/** The pack in use: where its files come from, its version, and the cards built from it. */
data class ActiveContent(
    val files: ContentFiles,
    val version: Int,
    val downloaded: Boolean,
    val deck: Map<GameMode, List<Card>>,
)

/**
 * Picks the downloaded pack when it is newer than the built-in one and in the format this app reads; otherwise the
 * built-in pack. A downloaded pack that is outdated (a newer APK brought a newer pool) or unreadable is deleted.
 */
fun resolveContent(builtIn: ContentFiles, store: ContentStore, log: StudyLog): ActiveContent {
    val bundled = parseContentManifest(builtIn.read(CONTENT_MANIFEST_FILE)) ?: error("assets/content.json is malformed")
    val downloaded = store.installedManifest()
    if (downloaded != null && prefersDownloaded(bundled, downloaded)) {
        val deck = runCatching { buildDeckFrom(store.files) }
        deck.onSuccess { return ActiveContent(store.files, downloaded.contentVersion, downloaded = true, deck = it) }
        log.debug(LogChannel.CONTENT) { "downloaded pool ${downloaded.contentVersion} unreadable, using built-in: ${deck.exceptionOrNull()}" }
    }
    if (downloaded != null) {
        log.debug(LogChannel.CONTENT) { "dropping downloaded pool ${downloaded.contentVersion} (built-in is ${bundled.contentVersion})" }
        store.clear()
    }
    return ActiveContent(builtIn, bundled.contentVersion, downloaded = false, deck = buildDeckFrom(builtIn))
}

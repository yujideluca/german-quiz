package dev.yuji.quiz.data

import dev.yuji.quiz.content.CONTENT_FILES
import dev.yuji.quiz.content.ContentCheck
import dev.yuji.quiz.content.ContentProblem
import dev.yuji.quiz.content.parseContentManifest
import dev.yuji.quiz.content.shouldDownloadContent
import dev.yuji.quiz.content.validateContentPack

/** Where new question packs come from: the `content/` folder of the update feed ([UpdateClient]). */
interface ContentFeed {
    fun fetchContentManifestText(): String
    fun fetchContentFile(path: String): ByteArray
}

sealed interface ContentUpdate {
    data object UpToDate : ContentUpdate
    data class Installed(val content: ActiveContent) : ContentUpdate
    data class Refused(val problem: ContentProblem, val detail: String) : ContentUpdate
}

/**
 * Asks the feed for a newer question pool and installs it only when it passes [validateContentPack] (QP-2).
 * Network failures are thrown to the caller; nothing is written unless the whole pack is valid.
 */
class ContentUpdater(private val feed: ContentFeed, private val store: ContentStore) {
    fun update(activeVersion: Int): ContentUpdate {
        val text = feed.fetchContentManifestText()
        val manifest = parseContentManifest(text) ?: return ContentUpdate.Refused(ContentProblem.UNREADABLE, "content.json")
        if (!shouldDownloadContent(manifest, activeVersion)) return ContentUpdate.UpToDate
        val bytes = CONTENT_FILES.associateWith { feed.fetchContentFile(it) }
        return when (val check = validateContentPack(manifest, bytes)) {
            is ContentCheck.Invalid -> ContentUpdate.Refused(check.problem, check.detail)
            is ContentCheck.Valid -> {
                store.install(text, bytes)
                ContentUpdate.Installed(ActiveContent(store.files, manifest.contentVersion, downloaded = true, deck = check.deck))
            }
        }
    }
}

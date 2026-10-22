package dev.yuji.quiz.data

// REQ QP-1, QP-2, QP-3

import dev.yuji.quiz.content.CONTENT_FILES
import dev.yuji.quiz.content.CONTENT_SCHEMA
import dev.yuji.quiz.content.ContentProblem
import dev.yuji.quiz.content.parseContentManifest
import dev.yuji.quiz.content.sha256
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.domain.GameMode
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole path a new pool takes on the phone: feed -> checks -> files/content -> deck in use. */
class ContentUpdaterTest {
    private val assets = File("src/main/assets")
    private val builtIn = DirContentFiles(assets)
    private val builtInVersion = parseContentManifest(File(assets, "content.json").readText())!!.contentVersion
    private val root = Files.createTempDirectory("content").toFile()
    private val store = ContentStore(root)

    /** The built-in pack plus one noun (two cards: article and plural), published as [version]. */
    private fun grownPack(version: Int = builtInVersion + 1, schema: Int = CONTENT_SCHEMA): Pair<String, Map<String, ByteArray>> {
        val nouns = File(assets, "vocab/nouns.json").readText().trimEnd().removeSuffix("]") +
            """,{"article":"die","singular":"Testblume","plural":"Testblumen","alsoValidPlurals":[]}]"""
        val bytes = CONTENT_FILES.associateWith { File(assets, it).readBytes() } + ("vocab/nouns.json" to nouns.toByteArray())
        val files = bytes.entries.joinToString(",") { "\"${it.key}\":\"${sha256(it.value)}\"" }
        return """{"schema":$schema,"contentVersion":$version,"files":{$files}}""" to bytes
    }

    private class FakeFeed(val manifest: String, val bytes: Map<String, ByteArray>, val failOn: String? = null) : ContentFeed {
        val requested = mutableListOf<String>()
        override fun fetchContentManifestText() = manifest
        override fun fetchContentFile(path: String): ByteArray {
            requested += path
            if (path == failOn) throw IOException("connection reset")
            return bytes.getValue(path)
        }
    }

    private fun resolve() = resolveContent(builtIn, store, NoopStudyLog)

    @Test fun `without a download the built-in pool is used`() {
        val active = resolve()
        assertFalse(active.downloaded)
        assertEquals(builtInVersion, active.version)
    }

    @Test fun `a newer valid pack is installed and used from then on, keeping every old card id`() {
        val before = resolve()
        val (manifest, bytes) = grownPack()
        val result = ContentUpdater(FakeFeed(manifest, bytes), store).update(before.version)
        assertTrue(result is ContentUpdate.Installed)
        val after = resolve()                                       // as on the next app start
        assertTrue(after.downloaded)
        assertEquals(builtInVersion + 1, after.version)
        assertEquals(before.deck.getValue(GameMode.ARTICLE).size + 1, after.deck.getValue(GameMode.ARTICLE).size)
        assertTrue(after.deck.values.flatten().map { it.id }.containsAll(before.deck.values.flatten().map { it.id }))
        assertEquals("die", after.deck.getValue(GameMode.ARTICLE).single { it.id == "article:Testblume" }.correctChoice)
        assertTrue(after.files.read("i18n/de.json").isNotEmpty())   // translations come from the same pack
    }

    @Test fun `an equal or older pack is not even downloaded`() {
        val (manifest, bytes) = grownPack(version = builtInVersion)
        val feed = FakeFeed(manifest, bytes)
        assertEquals(ContentUpdate.UpToDate, ContentUpdater(feed, store).update(builtInVersion))
        assertTrue(feed.requested.isEmpty())
    }

    @Test fun `a pack in another format is ignored`() {
        val (manifest, bytes) = grownPack(schema = CONTENT_SCHEMA + 1)
        assertEquals(ContentUpdate.UpToDate, ContentUpdater(FakeFeed(manifest, bytes), store).update(builtInVersion))
        assertNull(store.installedManifest())
    }

    @Test fun `a tampered file is refused and the pool in use stays`() {
        val (manifest, bytes) = grownPack()
        val tampered = bytes + ("vocab/verbs.json" to "[]".toByteArray())
        val result = ContentUpdater(FakeFeed(manifest, tampered), store).update(builtInVersion)
        assertEquals(ContentProblem.CHECKSUM_MISMATCH, (result as ContentUpdate.Refused).problem)
        assertNull(store.installedManifest())
        assertFalse(resolve().downloaded)
    }

    @Test fun `a broken connection mid-download changes nothing`() {
        val (manifest, bytes) = grownPack()
        val failing = FakeFeed(manifest, bytes, failOn = "i18n/de.json")
        assertTrue(runCatching { ContentUpdater(failing, store).update(builtInVersion) }.isFailure)
        assertNull(store.installedManifest())
    }

    @Test fun `a garbled manifest is refused`() {
        val result = ContentUpdater(FakeFeed("<html>rate limited</html>", emptyMap()), store).update(builtInVersion)
        assertTrue(result is ContentUpdate.Refused)
    }

    @Test fun `a downloaded pack that a newer app version outgrew is deleted`() {
        val (manifest, bytes) = grownPack(version = builtInVersion)  // e.g. installed earlier, then a new APK shipped it built in
        store.install(manifest, bytes)
        assertFalse(resolve().downloaded)
        assertNull(store.installedManifest())
    }

    @Test fun `an installed pack that became unreadable falls back to the built-in pool`() {
        val (manifest, bytes) = grownPack()
        store.install(manifest, bytes)
        File(root, "current/vocab/errors.json").writeText("{broken")
        val active = resolve()
        assertFalse(active.downloaded)
        assertEquals(builtInVersion, active.version)
        assertNull(store.installedManifest())
    }

    @Test fun `an install interrupted before the swap leaves the built-in pool`() {
        val (manifest, bytes) = grownPack()
        File(root, "staging/vocab").mkdirs()
        File(root, "staging/vocab/nouns.json").writeBytes(bytes.getValue("vocab/nouns.json"))  // no manifest yet
        assertFalse(resolve().downloaded)
        store.install(manifest, bytes)                              // the next attempt succeeds
        assertTrue(resolve().downloaded)
    }
}

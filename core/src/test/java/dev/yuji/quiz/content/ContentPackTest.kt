package dev.yuji.quiz.content

// REQ QP-1, QP-2, QP-3, QP-4

import dev.yuji.quiz.domain.GameMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPackTest {
    private val assets = File("../app/src/main/assets")
    private val bundledBytes = CONTENT_FILES.associateWith { File(assets, it).readBytes() }
    private val bundled = parseContentManifest(File(assets, CONTENT_MANIFEST_FILE).readText())!!

    private fun manifest(version: Int = 2, schema: Int = CONTENT_SCHEMA, bytes: Map<String, ByteArray> = bundledBytes) =
        ContentManifest(schema, version, bytes.mapValues { sha256(it.value) })

    // ---- QP-4: the built-in manifest describes the built-in files ----

    @Test fun `built-in manifest lists the checksum of every bundled pool file`() {
        assertEquals(CONTENT_SCHEMA, bundled.schema)
        assertEquals(CONTENT_FILES.toSet(), bundled.files.keys)
        CONTENT_FILES.forEach { assertEquals(it, sha256(bundledBytes.getValue(it)), bundled.files[it]) }
        assertTrue(bundled.contentVersion >= 1)
    }

    @Test fun `built-in pack passes the same check a download must pass`() {
        assertTrue(validateContentPack(bundled, bundledBytes) is ContentCheck.Valid)
    }

    // ---- QP-1: which pack is used ----

    @Test fun `only a newer pack in the supported format is downloaded`() {
        assertTrue(shouldDownloadContent(manifest(version = 6), activeVersion = 5))
        assertFalse(shouldDownloadContent(manifest(version = 5), activeVersion = 5))
        assertFalse(shouldDownloadContent(manifest(version = 4), activeVersion = 5))
        assertFalse(shouldDownloadContent(manifest(version = 9, schema = CONTENT_SCHEMA + 1), activeVersion = 5))
    }

    @Test fun `a downloaded pack wins only when it is newer than the built-in one`() {
        val builtIn = manifest(version = 5)
        assertFalse(prefersDownloaded(builtIn, null))
        assertTrue(prefersDownloaded(builtIn, manifest(version = 6)))
        assertFalse("a new APK with the same or newer pool wins", prefersDownloaded(builtIn, manifest(version = 5)))
        assertFalse(prefersDownloaded(builtIn, manifest(version = 3)))
        assertFalse(prefersDownloaded(builtIn, manifest(version = 9, schema = CONTENT_SCHEMA + 1)))
    }

    @Test fun `manifest parses with unknown fields and rejects garbage`() {
        val m = parseContentManifest("""{"schema":1,"contentVersion":3,"files":{"a":"b"},"commit":"abc","extra":true}""")
        assertEquals(ContentManifest(1, 3, mapOf("a" to "b"), "abc"), m)
        assertNull(parseContentManifest("<html>"))
        assertNull(parseContentManifest("""{"schema":1}"""))
    }

    // ---- QP-2: a pack is installed only when complete and usable ----

    private fun problemOf(m: ContentManifest, bytes: Map<String, ByteArray> = bundledBytes) =
        (validateContentPack(m, bytes) as? ContentCheck.Invalid)?.problem

    @Test fun `a wrong schema is refused`() =
        assertEquals(ContentProblem.UNSUPPORTED_SCHEMA, problemOf(manifest(schema = CONTENT_SCHEMA + 1)))

    @Test fun `a file missing from the manifest or the download is refused`() {
        val m = manifest()
        assertEquals(ContentProblem.MISSING_FILE, problemOf(m.copy(files = m.files - "vocab/verbs.json")))
        assertEquals(ContentProblem.MISSING_FILE, problemOf(m, bundledBytes - "i18n/pt.json"))
    }

    @Test fun `a changed byte is refused by its checksum`() {
        val tampered = bundledBytes.getValue("vocab/nouns.json").copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertEquals(ContentProblem.CHECKSUM_MISMATCH, problemOf(manifest(), bundledBytes + ("vocab/nouns.json" to tampered)))
    }

    @Test fun `a file that does not parse is refused even with a matching checksum`() {
        val broken = bundledBytes + ("vocab/verbs.json" to "{not json".toByteArray())
        assertEquals(ContentProblem.UNREADABLE, problemOf(manifest(bytes = broken), broken))
        val badI18n = bundledBytes + ("i18n/de.json" to "[1,2]".toByteArray())
        assertEquals(ContentProblem.UNREADABLE, problemOf(manifest(bytes = badI18n), badI18n))
    }

    @Test fun `a pack that would empty a mode is refused`() {
        val empty = bundledBytes + ("vocab/errors.json" to "[]".toByteArray())
        assertEquals(ContentProblem.EMPTY_MODE, problemOf(manifest(bytes = empty), empty))
    }

    // ---- QP-3: card ids stay stable, so progress follows the cards ----

    @Test fun `a grown pool keeps every old card id and reports how many were added`() {
        val nouns = File(assets, "vocab/nouns.json").readText()
        val grown = nouns.trimEnd().removeSuffix("]") +
            """,{"article":"der","singular":"Testbaum","plural":"Testbäume","alsoValidPlurals":[]}]"""
        val bytes = bundledBytes + ("vocab/nouns.json" to grown.toByteArray())
        val before = (validateContentPack(bundled, bundledBytes) as ContentCheck.Valid).deck
        val after = (validateContentPack(manifest(bytes = bytes), bytes) as ContentCheck.Valid).deck
        assertEquals(2, poolGrowth(before, after))   // one noun = one article card + one plural card
        val oldIds = before.values.flatten().map { it.id }.toSet()
        assertTrue(after.values.flatten().map { it.id }.toSet().containsAll(oldIds))
        assertNotNull(after.getValue(GameMode.ARTICLE).find { it.id == "article:Testbaum" })
    }
}

package dev.yuji.quiz.data

import dev.yuji.quiz.domain.CardPhase
import dev.yuji.quiz.domain.CardProgress
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** REQ PE-1 PE-2: the progress file, including fault injection (damaged file, leftover temp file). */
class FileProgressStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun file() = File(tmp.root, "progress.json")
    private val p = CardProgress("a", phase = CardPhase.REVIEW, intervalDays = 8, dueAtMillis = 99)

    @Test fun savedProgressSurvivesAReopen() {
        FileProgressStore(file()).save(p)
        assertEquals(mapOf("a" to p), FileProgressStore(file()).loadAll())
    }

    @Test fun aDamagedFileIsKeptAsACopyBeforeNewProgressIsWritten() {
        file().writeText("{ this is not json")
        val store = FileProgressStore(file())
        assertTrue(store.loadAll().isEmpty())               // the app still starts
        store.save(p)                                       // ... and the next answer is saved
        val copies = tmp.root.listFiles { f -> f.name.startsWith("progress.json.corrupt") }!!
        assertEquals("exactly one copy of the damaged file", 1, copies.size)
        assertEquals("{ this is not json", copies[0].readText())
        assertEquals(mapOf("a" to p), FileProgressStore(file()).loadAll())
    }

    @Test fun anIntactFileIsNeverCopied() {
        FileProgressStore(file()).save(p)
        FileProgressStore(file()).save(p.copy(cardId = "b"))
        assertFalse(tmp.root.listFiles()!!.any { it.name.contains("corrupt") })
    }

    @Test fun aLeftoverTempFileFromACrashDoesNotBreakTheNextSave() {
        File(tmp.root, "progress.json.tmp").writeText("half written")
        val store = FileProgressStore(file())
        store.save(p)
        assertEquals(mapOf("a" to p), FileProgressStore(file()).loadAll())
    }

    @Test fun clearRemovesTheFile() {
        val store = FileProgressStore(file())
        store.save(p); store.clear()
        assertTrue(FileProgressStore(file()).loadAll().isEmpty())
    }
}

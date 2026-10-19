package dev.yuji.quiz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressBackupTest {
    private val card = CardProgress("a", phase = CardPhase.REVIEW, intervalDays = 6, repetitions = 2)
    private val backup = ProgressBackup(
        exportedAtMillis = 1_700_000_000_000, appVersion = "1.5",
        progress = mapOf("a" to card), history = mapOf(20000L to 12), dailyGoal = 50, themeMode = "DARK",
    )

    @Test fun `round trips through the file format`() {
        assertEquals(BackupParse.Ok(backup), decodeBackup(encodeBackup(backup)))
    }

    @Test fun `garbage is rejected with a readable reason`() {
        assertTrue(decodeBackup("<html>") is BackupParse.Invalid)
        assertTrue(decodeBackup("""{"hello":1}""") is BackupParse.Invalid)
    }

    @Test fun `a file from a newer format is rejected`() {
        val newer = encodeBackup(backup.copy(format = BACKUP_FORMAT + 1))
        val result = decodeBackup(newer)
        assertTrue(result is BackupParse.Invalid && "newer" in result.reason)
    }

    @Test fun `untrustworthy entries are dropped and the goal is clamped`() {
        val dirty = backup.copy(
            progress = mapOf("a" to card, "wrong-key" to card),
            history = mapOf(1L to 5, 2L to 0, 3L to -4),
            dailyGoal = 100_000,
        )
        val ok = (decodeBackup(encodeBackup(dirty)) as BackupParse.Ok).backup
        assertEquals(setOf("a"), ok.progress.keys)
        assertEquals(mapOf(1L to 5), ok.history)
        assertEquals(clampDailyGoal(100_000), ok.dailyGoal)
    }
}

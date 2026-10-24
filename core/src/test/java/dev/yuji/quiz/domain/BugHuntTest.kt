package dev.yuji.quiz.domain

// REQ DG-1 DG-2 MG-1 UP-1 PE-3

import dev.yuji.quiz.content.CONTENT_FILES
import dev.yuji.quiz.content.ContentCheck
import dev.yuji.quiz.content.ContentManifest
import dev.yuji.quiz.content.ContentProblem
import dev.yuji.quiz.content.prefersDownloaded
import dev.yuji.quiz.content.shouldDownloadContent
import dev.yuji.quiz.content.validateContentPack
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Written by the bug-finding review (docs/TESTES-AUTOMATIZADOS.md). Expected values are written by hand from the
 * requirements, never computed with the function under test. The three findings F1-F3 of the record were decided by
 * Giovanni on 2026-10-24 ("deve") and are fixed.
 */
class BugHuntTest {
    // ---- daily goal: the grid is 10..100 in steps of 10 (DG-1) ----

    @Test fun `a huge stored goal snaps to the maximum, not the minimum`() {
        assertEquals(MAX_DAILY_GOAL, clampDailyGoal(Int.MAX_VALUE))
        assertEquals(MAX_DAILY_GOAL, clampDailyGoal(2_000_000_000))
    }

    @Test fun `a hugely negative stored goal snaps to the minimum`() {
        assertEquals(MIN_DAILY_GOAL, clampDailyGoal(Int.MIN_VALUE))
    }

    @Test fun `goals snap to the nearest step and halves go up`() {
        val expected = mapOf(-5 to 10, 0 to 10, 4 to 10, 5 to 10, 14 to 10, 15 to 20, 16 to 20, 95 to 100, 99 to 100, 104 to 100, 105 to 100, 1000 to 100)
        for ((input, want) in expected) assertEquals("goal $input", want, clampDailyGoal(input))
    }

    @Test fun `a backup with an overflowing goal imports as the maximum goal`() {
        val text = encodeBackup(ProgressBackup(exportedAtMillis = 1, appVersion = "x", progress = emptyMap(), history = emptyMap(), dailyGoal = Int.MAX_VALUE))
        assertEquals(MAX_DAILY_GOAL, (decodeBackup(text) as BackupParse.Ok).backup.dailyGoal)
    }

    @Test fun `the bar fraction is defined even for a zero goal`() {
        assertEquals(0f, DailyProgress(answered = 0, goal = 0).fraction, 0f)
        assertEquals(1f, DailyProgress(answered = 3, goal = 0).fraction, 0f)
    }

    // ---- backup import drops what cannot be trusted (PE-3) ----

    @Test fun `import drops entries whose key differs from the card id and non-positive history`() {
        val good = CardProgress("a")
        val text = encodeBackup(ProgressBackup(
            exportedAtMillis = 1, appVersion = "x", dailyGoal = 20,
            progress = mapOf("a" to good, "b" to CardProgress("c")),
            history = mapOf(1L to 5, 2L to 0, 3L to -4),
        ))
        val ok = (decodeBackup(text) as BackupParse.Ok).backup
        assertEquals(setOf("a"), ok.progress.keys)
        assertEquals(mapOf(1L to 5), ok.history)
    }

    // ---- streaks and calendar (CA-1) ----

    @Test fun `an empty today keeps yesterday's streak but an empty yesterday ends it`() {
        val h = mapOf(10L to 1, 9L to 1, 8L to 1)
        assertEquals(3, currentStreak(h, todayEpoch = 11))
        assertEquals(0, currentStreak(h, todayEpoch = 12))
        assertEquals(3, currentStreak(h, todayEpoch = 10))
    }

    @Test fun `the longest streak ignores zero-count days and insertion order`() {
        val h = linkedMapOf(7L to 1, 5L to 2, 6L to 0, 4L to 1, 3L to 1, 2L to 1)
        assertEquals(4, longestStreak(h)) // 2,3,4,5 in a row; 6 is empty; 7 stands alone
    }

    @Test fun `a calendar month starts on the right weekday and counts only its own days`() {
        val cal = buildCalendar(mapOf(LocalDate.of(2026, 10, 1).toEpochDay() to 4), YearMonth.of(2026, 10), LocalDate.of(2026, 10, 24))
        assertEquals(3, cal.leadingBlanks) // 1 October 2026 is a Thursday: Mon, Tue, Wed are blank
        assertEquals(31, cal.days.size)
        assertEquals(4, cal.answersInMonth)
        assertEquals(1, cal.trainedDaysInMonth)
        assertFalse(cal.canGoNext)
    }

    // ---- downloaded content packs (QP-2) ----

    private fun manifest(schema: Int = 1, version: Int = 5, files: Map<String, String> = CONTENT_FILES.associateWith { "0".repeat(64) }) =
        ContentManifest(schema, version, files)

    @Test fun `a pack of another schema is refused before anything else`() {
        val r = validateContentPack(manifest(schema = 2), emptyMap())
        assertEquals(ContentProblem.UNSUPPORTED_SCHEMA, (r as ContentCheck.Invalid).problem)
    }

    @Test fun `a pack with a file missing from the manifest or the download is refused`() {
        val noEntry = validateContentPack(manifest(files = CONTENT_FILES.drop(1).associateWith { "0" }), emptyMap())
        assertEquals(ContentProblem.MISSING_FILE, (noEntry as ContentCheck.Invalid).problem)
        val noBytes = validateContentPack(manifest(), emptyMap())
        assertEquals(ContentProblem.MISSING_FILE, (noBytes as ContentCheck.Invalid).problem)
    }

    @Test fun `a pack whose bytes do not match the listed checksum is refused`() {
        val bytes = CONTENT_FILES.associateWith { "[]".toByteArray() }
        val r = validateContentPack(manifest(), bytes)
        assertEquals(ContentProblem.CHECKSUM_MISMATCH, (r as ContentCheck.Invalid).problem)
    }

    @Test fun `only a strictly newer pack of the supported schema is downloaded or preferred`() {
        assertTrue(shouldDownloadContent(manifest(version = 6), activeVersion = 5))
        assertFalse(shouldDownloadContent(manifest(version = 5), activeVersion = 5))
        assertFalse(shouldDownloadContent(manifest(version = 4), activeVersion = 5))
        assertFalse(shouldDownloadContent(manifest(schema = 2, version = 9), activeVersion = 5))
        assertFalse(prefersDownloaded(manifest(version = 5), manifest(version = 5)))
        assertFalse(prefersDownloaded(manifest(version = 5), null))
        assertTrue(prefersDownloaded(manifest(version = 5), manifest(version = 6)))
    }

    // ---- update check spacing (UP-1) ----

    @Test fun `the update check waits for the gap but runs at exactly the gap`() {
        assertTrue(shouldCheckForUpdate(1_000, null))
        assertFalse(shouldCheckForUpdate(1_000 + UPDATE_CHECK_MIN_GAP_MILLIS - 1, 1_000))
        assertTrue(shouldCheckForUpdate(1_000 + UPDATE_CHECK_MIN_GAP_MILLIS, 1_000))
    }

    @Test fun `a clock set back does not block the update check`() {
        assertTrue(shouldCheckForUpdate(nowMillis = 1_000, lastCheckMillis = 5_000_000))
    }

    // ---- searching removed and suspended cards (MG-1) ----

    private fun managed(prompt: String) =
        ManagedCard(Card(id = "n1", mode = GameMode.entries.first(), prompt = prompt, context = null, choices = listOf("x"), correctChoice = "x", explanation = "", shuffleChoices = false), CardStatus.REMOVED, 0L)

    @Test fun `search ignores case and umlauts`() {
        val items = listOf(managed("Stühle"), managed("Tisch"))
        assertEquals(listOf("Stühle"), searchManagedCards(items, "STUHLE").map { it.card.prompt })
        assertEquals(2, searchManagedCards(items, "  ").size)
    }

    @Test fun `search finds the sharp s by its double s spelling`() {
        assertEquals(1, searchManagedCards(listOf(managed("Straße")), "strasse").size)
    }
}

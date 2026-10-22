package dev.yuji.quiz.domain

// REQ DG-1 DG-3

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The per-day answer count behind the daily goal, tested in the core module on its own. */
class AnswerHistoryTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun millis(date: String, hour: Int, minute: Int = 0) =
        LocalDate.parse(date).atTime(hour, minute).atZone(berlin).toInstant().toEpochMilli()

    @Test fun theStudyDayRestartsAtLocalMidnight() {
        val before = epochDayOf(millis("2026-10-21", 23, 59), berlin)
        val after = epochDayOf(millis("2026-10-22", 0, 0), berlin)
        assertEquals(LocalDate.parse("2026-10-21").toEpochDay(), before)
        assertEquals(before + 1, after)
    }

    @Test fun everyAnswerRaisesThatDayByExactlyOne() {
        val day = 20_000L
        val history = emptyMap<Long, Int>().plusAnswer(day).plusAnswer(day).plusAnswer(day + 1)
        assertEquals(2, history.answeredOn(day))
        assertEquals(1, history.answeredOn(day + 1))
        assertEquals(0, history.answeredOn(day - 1))
    }
}

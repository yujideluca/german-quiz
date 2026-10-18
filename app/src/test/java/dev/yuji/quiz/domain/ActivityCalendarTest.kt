package dev.yuji.quiz.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityCalendarTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun day(d: Int) = LocalDate.of(2026, 10, d).toEpochDay()
    private fun history(vararg entries: Pair<Int, Int>): AnswerHistory = entries.associate { day(it.first) to it.second }

    @Test fun streakCountsConsecutiveTrainedDaysEndingToday() {
        assertEquals(3, currentStreak(history(16 to 5, 17 to 40, 18 to 1), day(18)))
    }

    @Test fun emptyTodayDoesNotBreakAStreakThatRanThroughYesterday() {
        assertEquals(2, currentStreak(history(16 to 5, 17 to 40), day(18)))
    }

    @Test fun aMissedDayBreaksTheStreak() {
        assertEquals(0, currentStreak(history(15 to 5, 16 to 5), day(18)))
        assertEquals(1, currentStreak(history(15 to 5, 18 to 5), day(18)))
    }

    @Test fun longestStreakFindsTheBestRun() {
        assertEquals(3, longestStreak(history(1 to 1, 2 to 1, 4 to 9, 5 to 9, 6 to 9, 10 to 1)))
        assertEquals(0, longestStreak(emptyMap()))
    }

    @Test fun mondayFirstLayoutAndMonthTotals() {
        val cal = buildCalendar(history(1 to 10, 2 to 30, 18 to 5), YearMonth.of(2026, 10), today)
        assertEquals(31, cal.days.size)
        assertEquals(3, cal.leadingBlanks)                    // 1 Oct 2026 is a Thursday
        assertEquals(3, cal.trainedDaysInMonth)
        assertEquals(45, cal.answersInMonth)
        assertTrue(cal.days[17].isToday)
        assertTrue(cal.days[18].isFuture)
        assertFalse(cal.canGoNext)
        assertTrue(buildCalendar(emptyMap(), YearMonth.of(2026, 9), today).canGoNext)
    }
}

class HeatColorTest {
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test fun rampStartsLightYellowAndEndsOrangeRed() {
        assertEquals(0xFFF4B5, heatColorRgb(0))
        assertEquals(0xE4401C, heatColorRgb(HEAT_FULL_ANSWERS))
        assertEquals(heatColorRgb(HEAT_FULL_ANSWERS), heatColorRgb(500))
    }

    @Test fun moreAnswersNeverGetLighterOrMoreGreen() {
        val colors = (1..HEAT_FULL_ANSWERS).map(::heatColorRgb)
        colors.zipWithNext().forEach { (a, c) -> assertTrue(g(c) <= g(a)); assertTrue(b(c) <= b(a)); assertTrue(r(c) <= r(a) + 1) }
        assertTrue("always warm: strong red, little blue", colors.all { r(it) >= 0xE4 && b(it) <= 0xB5 })
    }
}

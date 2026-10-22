package dev.yuji.quiz.domain

import java.time.LocalDate
import java.time.YearMonth

/** A day with at least one answer counts as trained; the streak is the run of consecutive trained days. */
data class CalendarDay(val date: LocalDate, val answered: Int, val isToday: Boolean, val isFuture: Boolean)

data class ActivityCalendar(
    val month: YearMonth,
    /** Empty cells before day 1 so that weeks start on Monday. */
    val leadingBlanks: Int,
    val days: List<CalendarDay>,
    val currentStreak: Int,
    val longestStreak: Int,
    val trainedDaysInMonth: Int,
    val answersInMonth: Int,
    val canGoNext: Boolean,
)

/** Consecutive trained days ending today; a still-empty today does not break a streak that ran through yesterday. */
fun currentStreak(history: AnswerHistory, todayEpoch: Long): Int {
    var day = if (history.answeredOn(todayEpoch) > 0) todayEpoch else todayEpoch - 1
    var streak = 0
    while (history.answeredOn(day) > 0) { streak++; day-- }
    return streak
}

fun longestStreak(history: AnswerHistory): Int {
    var best = 0
    var run = 0
    var previous: Long? = null
    for (day in history.filterValues { it > 0 }.keys.sorted()) {
        run = if (previous != null && day == previous + 1) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    return best
}

fun buildCalendar(history: AnswerHistory, month: YearMonth, today: LocalDate): ActivityCalendar {
    val days = (1..month.lengthOfMonth()).map { d ->
        val date = month.atDay(d)
        CalendarDay(date, history.answeredOn(date.toEpochDay()), date == today, date.isAfter(today))
    }
    return ActivityCalendar(
        month = month,
        leadingBlanks = month.atDay(1).dayOfWeek.value - 1,
        days = days,
        currentStreak = currentStreak(history, today.toEpochDay()),
        longestStreak = longestStreak(history),
        trainedDaysInMonth = days.count { it.answered > 0 },
        answersInMonth = days.sumOf { it.answered },
        canGoNext = month < YearMonth.from(today),
    )
}

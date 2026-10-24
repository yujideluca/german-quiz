package dev.yuji.quiz.domain

import java.time.Instant
import java.time.ZoneId

const val MIN_DAILY_GOAL = 10
const val MAX_DAILY_GOAL = 100
const val DAILY_GOAL_STEP = 10
const val DEFAULT_DAILY_GOAL = 20

/** Answers given per study day (epoch day -> count, all quizzes together). Days without answers are absent. */
typealias AnswerHistory = Map<Long, Int>

/** Today's training status shown to the player. */
data class DailyProgress(val answered: Int, val goal: Int) {
    val reached: Boolean get() = answered >= goal
    val fraction: Float get() = (answered.toFloat() / goal).coerceIn(0f, 1f)
}

/** Snaps any stored value onto the slider's grid (10, 20, ... 100). */
fun clampDailyGoal(value: Int): Int =
    // Clamp first: snapping a huge value to the grid overflowed Int and turned the maximum into the minimum.
    Math.round(value.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL).toDouble() / DAILY_GOAL_STEP).toInt() * DAILY_GOAL_STEP

fun epochDayOf(nowMillis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

fun AnswerHistory.plusAnswer(epochDay: Long): AnswerHistory = this + (epochDay to (this[epochDay] ?: 0) + 1)

fun AnswerHistory.answeredOn(epochDay: Long): Int = this[epochDay] ?: 0

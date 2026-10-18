package dev.yuji.quiz.data

import android.content.Context
import dev.yuji.quiz.domain.AnswerHistory
import dev.yuji.quiz.domain.DEFAULT_DAILY_GOAL
import dev.yuji.quiz.domain.clampDailyGoal

/** Daily-goal setting and the answers given on each study day. */
interface TrainingStore {
    var dailyGoal: Int
    var history: AnswerHistory
}

class InMemoryTrainingStore(
    override var dailyGoal: Int = DEFAULT_DAILY_GOAL,
    override var history: AnswerHistory = emptyMap(),
) : TrainingStore

/** "day:count;day:count" <-> map. Malformed entries are skipped rather than failing the whole history. */
internal fun encodeHistory(history: AnswerHistory) = history.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

internal fun decodeHistory(text: String): AnswerHistory = text.split(';').mapNotNull { entry ->
    val (day, count) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
    val d = day.toLongOrNull() ?: return@mapNotNull null
    val c = count.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
    d to c
}.toMap()

class SharedPreferencesTrainingStore(context: Context) : TrainingStore {
    private val prefs = context.getSharedPreferences("training", Context.MODE_PRIVATE)

    override var dailyGoal: Int
        get() = clampDailyGoal(prefs.getInt(KEY_GOAL, DEFAULT_DAILY_GOAL))
        set(value) = prefs.edit().putInt(KEY_GOAL, clampDailyGoal(value)).apply()

    override var history: AnswerHistory
        get() = prefs.getString(KEY_HISTORY, null)?.let(::decodeHistory) ?: legacyTally()
        set(value) = prefs.edit().putString(KEY_HISTORY, encodeHistory(value)).apply()

    /** Before the calendar existed only today's count was stored (tally_day / tally_count). */
    private fun legacyTally(): AnswerHistory {
        val day = prefs.getLong(KEY_LEGACY_DAY, 0)
        val count = prefs.getInt(KEY_LEGACY_COUNT, 0)
        return if (day > 0 && count > 0) mapOf(day to count) else emptyMap()
    }

    private companion object {
        const val KEY_GOAL = "daily_goal"
        const val KEY_HISTORY = "history"
        const val KEY_LEGACY_DAY = "tally_day"
        const val KEY_LEGACY_COUNT = "tally_count"
    }
}

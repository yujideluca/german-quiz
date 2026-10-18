package dev.yuji.quiz.data

import android.content.Context
import dev.yuji.quiz.domain.DEFAULT_DAILY_GOAL
import dev.yuji.quiz.domain.DailyTally
import dev.yuji.quiz.domain.clampDailyGoal

/** Daily-goal setting and today's answer count. */
interface TrainingStore {
    var dailyGoal: Int
    var tally: DailyTally
}

class InMemoryTrainingStore(
    override var dailyGoal: Int = DEFAULT_DAILY_GOAL,
    override var tally: DailyTally = DailyTally(0, 0),
) : TrainingStore

class SharedPreferencesTrainingStore(context: Context) : TrainingStore {
    private val prefs = context.getSharedPreferences("training", Context.MODE_PRIVATE)

    override var dailyGoal: Int
        get() = clampDailyGoal(prefs.getInt(KEY_GOAL, DEFAULT_DAILY_GOAL))
        set(value) = prefs.edit().putInt(KEY_GOAL, clampDailyGoal(value)).apply()

    override var tally: DailyTally
        get() = DailyTally(prefs.getLong(KEY_DAY, 0), prefs.getInt(KEY_COUNT, 0))
        set(value) = prefs.edit().putLong(KEY_DAY, value.epochDay).putInt(KEY_COUNT, value.answered).apply()

    private companion object {
        const val KEY_GOAL = "daily_goal"
        const val KEY_DAY = "tally_day"
        const val KEY_COUNT = "tally_count"
    }
}

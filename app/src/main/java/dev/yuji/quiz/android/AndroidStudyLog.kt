package dev.yuji.quiz.android

import android.util.Log
import dev.yuji.quiz.debug.DebugFlags
import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.StudyLog

/** Writes to logcat under tags "Quiz/<CHANNEL>". Filter with: adb logcat -s Quiz/SCHEDULER */
class AndroidStudyLog(private val flags: DebugFlags) : StudyLog {
    override fun isEnabled(channel: LogChannel) = channel in flags.enabledChannels
    override fun write(channel: LogChannel, message: String) {
        Log.d("Quiz/$channel", message)
    }
}

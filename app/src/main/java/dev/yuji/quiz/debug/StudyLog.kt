package dev.yuji.quiz.debug

/** Subsystems that can be traced independently. Enable them through [DebugFlags]. */
enum class LogChannel { CONTENT, PIPELINE, SCHEDULER, SESSION, STORAGE, UI }

interface StudyLog {
    fun isEnabled(channel: LogChannel): Boolean
    fun write(channel: LogChannel, message: String)
}

/** Lazy logging: the message lambda only runs when the channel is enabled. */
inline fun StudyLog.debug(channel: LogChannel, message: () -> String) {
    if (isEnabled(channel)) write(channel, message())
}

object NoopStudyLog : StudyLog {
    override fun isEnabled(channel: LogChannel) = false
    override fun write(channel: LogChannel, message: String) = Unit
}

/** Collects lines in memory; used by unit tests to assert on traces. */
class InMemoryStudyLog(private val enabled: Set<LogChannel> = LogChannel.entries.toSet()) : StudyLog {
    val lines = mutableListOf<String>()
    override fun isEnabled(channel: LogChannel) = channel in enabled
    override fun write(channel: LogChannel, message: String) {
        lines += "[$channel] $message"
    }
}

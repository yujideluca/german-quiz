package dev.yuji.quiz.debug

/** Single place that decides what diagnostics are active. */
data class DebugFlags(
    val enabledChannels: Set<LogChannel> = emptySet(),
    /** Draws card id, phase, ease, interval and due time under each question. */
    val showCardOverlay: Boolean = false,
) {
    companion object {
        val OFF = DebugFlags()
        val ALL = DebugFlags(LogChannel.entries.toSet(), showCardOverlay = true)
        fun forBuild(isDebugBuild: Boolean) = if (isDebugBuild) ALL else OFF
    }
}

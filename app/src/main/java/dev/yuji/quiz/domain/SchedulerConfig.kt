package dev.yuji.quiz.domain

/** Anki-style (SM-2 variant) parameters. Defaults mirror Anki's defaults. */
data class SchedulerConfig(
    val learningStepsMinutes: List<Int> = listOf(10),
    val relearningStepsMinutes: List<Int> = listOf(10),
    val graduatingIntervalDays: Int = 3,
    val easyIntervalDays: Int = 5,
    val startingEase: Double = 2.5,
    val minimumEase: Double = 1.3,
    val easyBonus: Double = 1.3,
    val hardIntervalFactor: Double = 1.2,
    val intervalModifier: Double = 1.0,
    /** Fraction of the old interval kept after a lapse (Anki default 0 -> back to 1 day). */
    val lapseIntervalFactor: Double = 0.0,
    val maximumIntervalDays: Int = 36_500,
    /** Cards in learning due within this window are shown in the same session (0 = a missed card waits its full step). */
    val learnAheadMinutes: Int = 0,
) {
    init {
        require(learningStepsMinutes.isNotEmpty() && relearningStepsMinutes.isNotEmpty()) { "steps must not be empty" }
    }
}

/** How a multiple-choice answer maps to an Anki grade. */
data class GraderConfig(
    /** A correct answer on a mature card faster than this counts as EASY. */
    val easyMaxMillis: Long = 2_500,
    /** A correct answer slower than this counts as HARD. */
    val hardMinMillis: Long = 10_000,
)

data class QueueLimits(
    val maxReviewsPerSession: Int = 100,
    /** One new card is mixed in after this many reviews. */
    val reviewsPerNewCard: Int = 3,
)

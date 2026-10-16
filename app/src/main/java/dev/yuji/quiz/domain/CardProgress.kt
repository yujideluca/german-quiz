package dev.yuji.quiz.domain

import kotlinx.serialization.Serializable

enum class ReviewGrade { AGAIN, HARD, GOOD, EASY }

enum class CardPhase { NEW, LEARNING, REVIEW, RELEARNING }

/** Everything the scheduler remembers about one card. */
@Serializable
data class CardProgress(
    val cardId: String,
    val phase: CardPhase = CardPhase.NEW,
    /** Index into the learning / relearning steps while in those phases. */
    val learningStep: Int = 0,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val dueAtMillis: Long = 0L,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val firstSeenAtMillis: Long? = null,
    val lastReviewedAtMillis: Long? = null,
    /** Hidden from study until this instant (0 = not suspended). */
    val suspendedUntilMillis: Long = 0L,
    /** Permanently excluded from the pool until restored in Settings. */
    val removed: Boolean = false,
)

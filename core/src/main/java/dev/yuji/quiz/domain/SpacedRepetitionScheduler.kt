package dev.yuji.quiz.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure, deterministic Anki-style scheduler (no fuzz, no clock access).
 *
 *  NEW/LEARNING  -> walks learning steps, graduates to REVIEW
 *  REVIEW        -> interval grows by ease factor; AGAIN is a lapse -> RELEARNING
 *  RELEARNING    -> walks relearning steps, returns to REVIEW with the reduced interval
 */
object SpacedRepetitionScheduler {

    fun freshProgress(cardId: String, config: SchedulerConfig = SchedulerConfig()) =
        CardProgress(cardId = cardId, easeFactor = config.startingEase)

    fun scheduleNext(
        progress: CardProgress,
        grade: ReviewGrade,
        nowMillis: Long,
        config: SchedulerConfig = SchedulerConfig(),
    ): CardProgress {
        val stamped = progress.copy(
            repetitions = progress.repetitions + 1,
            firstSeenAtMillis = progress.firstSeenAtMillis ?: nowMillis,
            lastReviewedAtMillis = nowMillis,
        )
        return when (progress.phase) {
            CardPhase.NEW, CardPhase.LEARNING -> learn(stamped, grade, nowMillis, config)
            CardPhase.REVIEW -> review(stamped, grade, nowMillis, config)
            CardPhase.RELEARNING -> relearn(stamped, grade, nowMillis, config)
        }
    }

    // ---- learning ---------------------------------------------------------------------------

    private fun learn(p: CardProgress, grade: ReviewGrade, now: Long, c: SchedulerConfig): CardProgress {
        val steps = c.learningStepsMinutes
        val step = if (p.phase == CardPhase.NEW) 0 else p.learningStep.coerceIn(0, steps.lastIndex)
        fun graduate(days: Int) = p.copy(
            phase = CardPhase.REVIEW, learningStep = 0,
            intervalDays = days, dueAtMillis = now + days * DAY_MILLIS,
        )
        return when (grade) {
            ReviewGrade.AGAIN -> inStep(p, CardPhase.LEARNING, 0, now, steps)
            ReviewGrade.HARD -> inStep(p, CardPhase.LEARNING, step, now, steps)
            ReviewGrade.GOOD ->
                if (step + 1 >= steps.size) graduate(c.graduatingIntervalDays)
                else inStep(p, CardPhase.LEARNING, step + 1, now, steps)
            ReviewGrade.EASY -> graduate(c.easyIntervalDays)
        }
    }

    private fun relearn(p: CardProgress, grade: ReviewGrade, now: Long, c: SchedulerConfig): CardProgress {
        val steps = c.relearningStepsMinutes
        val step = p.learningStep.coerceIn(0, steps.lastIndex)
        fun back(days: Int) = p.copy(
            phase = CardPhase.REVIEW, learningStep = 0,
            intervalDays = days, dueAtMillis = now + days * DAY_MILLIS,
        )
        return when (grade) {
            ReviewGrade.AGAIN -> inStep(p, CardPhase.RELEARNING, 0, now, steps)
            ReviewGrade.HARD -> inStep(p, CardPhase.RELEARNING, step, now, steps)
            ReviewGrade.GOOD ->
                if (step + 1 >= steps.size) back(max(1, p.intervalDays))
                else inStep(p, CardPhase.RELEARNING, step + 1, now, steps)
            ReviewGrade.EASY -> back(min(c.maximumIntervalDays, max(1, p.intervalDays) + 1))
        }
    }

    private fun inStep(p: CardProgress, phase: CardPhase, step: Int, now: Long, steps: List<Int>) =
        p.copy(phase = phase, learningStep = step, dueAtMillis = now + steps[step] * MINUTE_MILLIS)

    // ---- review -----------------------------------------------------------------------------

    private fun review(p: CardProgress, grade: ReviewGrade, now: Long, c: SchedulerConfig): CardProgress {
        val old = max(1, p.intervalDays)
        fun grow(factor: Double, floor: Int) =
            min(c.maximumIntervalDays, max(floor, (old * factor * c.intervalModifier).roundToInt()))
        return when (grade) {
            ReviewGrade.AGAIN -> {
                val reduced = max(1, (old * c.lapseIntervalFactor).roundToInt())
                inStep(
                    p.copy(
                        lapses = p.lapses + 1,
                        easeFactor = max(c.minimumEase, p.easeFactor - 0.20),
                        intervalDays = reduced,
                    ),
                    CardPhase.RELEARNING, 0, now, c.relearningStepsMinutes,
                )
            }
            ReviewGrade.HARD -> due(
                p.copy(easeFactor = max(c.minimumEase, p.easeFactor - 0.15)),
                grow(c.hardIntervalFactor, old + 1), now,
            )
            ReviewGrade.GOOD -> due(p, grow(p.easeFactor, old + 1), now)
            ReviewGrade.EASY -> due(
                p.copy(easeFactor = p.easeFactor + 0.15),
                grow(p.easeFactor * c.easyBonus, old + 2), now,
            )
        }
    }

    private fun due(p: CardProgress, days: Int, now: Long) =
        p.copy(phase = CardPhase.REVIEW, learningStep = 0, intervalDays = days, dueAtMillis = now + days * DAY_MILLIS)
}

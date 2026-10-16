package dev.yuji.quiz.domain

/**
 * Multiple-choice answers carry no self-assessment, so the grade is inferred:
 * wrong -> AGAIN, slow -> HARD, fast on a mature card -> EASY, otherwise GOOD.
 * EASY is never granted to cards still in learning (a 1-in-3 guess must not skip steps).
 */
object AnswerGrader {
    fun grade(
        correct: Boolean,
        responseMillis: Long,
        phase: CardPhase,
        config: GraderConfig = GraderConfig(),
    ): ReviewGrade = when {
        !correct -> ReviewGrade.AGAIN
        responseMillis >= config.hardMinMillis -> ReviewGrade.HARD
        responseMillis <= config.easyMaxMillis && phase == CardPhase.REVIEW -> ReviewGrade.EASY
        else -> ReviewGrade.GOOD
    }
}

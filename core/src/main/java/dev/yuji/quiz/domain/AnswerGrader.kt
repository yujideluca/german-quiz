package dev.yuji.quiz.domain

/**
 * Multiple-choice answers carry no self-assessment, so the grade is inferred:
 * wrong -> AGAIN, slow -> HARD, fast on a mature card -> EASY, otherwise GOOD.
 * EASY and HARD only apply to cards already in REVIEW. A card still in (re)learning that is answered
 * correctly always passes, however long the player thought: a correct answer must never bring the card back
 * after 10 minutes (requirements SP-1, SP-4).
 */
object AnswerGrader {
    fun grade(
        correct: Boolean,
        responseMillis: Long,
        phase: CardPhase,
        config: GraderConfig = GraderConfig(),
    ): ReviewGrade = when {
        !correct -> ReviewGrade.AGAIN
        phase == CardPhase.REVIEW && responseMillis >= config.hardMinMillis -> ReviewGrade.HARD
        responseMillis <= config.easyMaxMillis && phase == CardPhase.REVIEW -> ReviewGrade.EASY
        else -> ReviewGrade.GOOD
    }
}

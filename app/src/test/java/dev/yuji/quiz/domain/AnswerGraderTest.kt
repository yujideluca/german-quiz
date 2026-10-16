package dev.yuji.quiz.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerGraderTest {
    @Test fun wrongIsAgain() = assertEquals(ReviewGrade.AGAIN, AnswerGrader.grade(false, 500, CardPhase.REVIEW))
    @Test fun slowCorrectIsHard() = assertEquals(ReviewGrade.HARD, AnswerGrader.grade(true, 12_000, CardPhase.REVIEW))
    @Test fun fastCorrectOnMatureIsEasy() = assertEquals(ReviewGrade.EASY, AnswerGrader.grade(true, 1_000, CardPhase.REVIEW))
    @Test fun fastCorrectInLearningIsOnlyGood() = assertEquals(ReviewGrade.GOOD, AnswerGrader.grade(true, 1_000, CardPhase.LEARNING))
    @Test fun normalCorrectIsGood() = assertEquals(ReviewGrade.GOOD, AnswerGrader.grade(true, 5_000, CardPhase.REVIEW))
}

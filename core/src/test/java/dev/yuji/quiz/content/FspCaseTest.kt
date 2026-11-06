package dev.yuji.quiz.content

// REQ FS-1 FS-2 FS-3 FS-4 FS-5

import dev.yuji.quiz.domain.CardPhase
import dev.yuji.quiz.domain.GameMode
import dev.yuji.quiz.domain.ReviewGrade
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FspCaseTest {
    private val cases = parseFspCases(File("../app/src/main/assets/vocab/fsp_cases.json").readText())
    private val listed = File("../tools/data/fsp_cases.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split("|") }

    private val partSizes = mapOf(1 to 16, 2 to 7, 3 to 11)

    @Test fun everyCaseOfTheListIsBuiltInListOrder() =
        assertEquals(listed.map { it[0] }, cases.map { it.id })

    @Test fun casesAreGroupedByAreaOfMedicineInListOrder() {
        assertEquals(listed.map { it[1] }, cases.map { it.area })
        // each area forms one block: once left, an area never comes back
        val blocks = cases.map { it.area }.fold(listOf<String>()) { acc, a -> if (acc.lastOrNull() == a) acc else acc + a }
        assertEquals(blocks.size, blocks.toSet().size)
        val deck = CardFactory.buildDeck(emptyList(), emptyList(), fspCases = cases).getValue(GameMode.FSP_CASE)
        assertEquals(cases.map { "fsp:${it.id}" }, deck.map { it.id })
    }

    @Test fun everyCaseFollowsTheOneTemplate() = cases.forEach { c ->
        assertEquals(c.id, partSizes, c.steps.groupingBy { it.part }.eachCount())
        assertEquals(c.id, List(5) { "Fachbegriffe" }, c.steps.takeLast(5).map { it.section })
        c.steps.forEachIndexed { i, s ->
            assertEquals("${c.id} step $i", 4, s.options.toSet().size)
            assertTrue("${c.id} step $i", s.feedback[s.answer].startsWith("Richtig"))
            assertEquals("${c.id} step $i", 1, s.feedback.count { it.startsWith("Richtig") })
        }
    }

    @Test fun theTitleNeverNamesTheDiagnosis() = cases.forEach { c ->
        val words = Regex("[A-Za-zÄÖÜäöüß]{6,}").findAll(c.diagnosis).map { it.value.lowercase() }
            .filterNot { it in setOf("akutes", "akuter", "akute", "chronische") }
        words.forEach { assertFalse("${c.id}: '$it' in title", it in c.title.lowercase()) }
        val card = CardFactory.fspCaseCard(c)
        assertFalse(card.prompt.contains(c.diagnosis))
        assertEquals(c.diagnosis, card.explanation)
    }

    private val case = cases.first()
    private fun run() = FspCaseRun(case, seed = 42)

    @Test fun aWrongPickIsShownAndThePlayerPicksAgain() {
        val step = case.steps[0]
        val wrong = (0..3).first { it != step.answer }
        val afterWrong = run().pick(wrong)
        assertEquals(setOf(wrong), afterWrong.wrongPicks)
        assertFalse(afterWrong.solved)
        assertSame("the same wrong pick twice is ignored", afterWrong, afterWrong.pick(wrong))
        assertSame("cannot move on before solving", afterWrong, afterWrong.next())
        val solved = afterWrong.pick(step.answer)
        assertTrue(solved.solved)
        assertEquals("only a first-try right answer counts", listOf(false), solved.firstTry)
        assertSame("picks after solving are ignored", solved, solved.pick(wrong))
        val moved = solved.next()
        assertEquals(1, moved.stepIndex)
        assertTrue(moved.wrongPicks.isEmpty())
    }

    @Test fun answerOrderIsAShuffledPermutationThatStaysPutWithinAStep() {
        val r = run()
        assertEquals((0..3).toList(), r.order().sorted())
        assertEquals(r.order(), r.pick((0..3).first { it != case.steps[0].answer }).order())
        val orders = (0 until case.steps.size).map { r.copy(stepIndex = it).order() }
        assertTrue("answers must not always sit in the file's order", orders.any { it != listOf(0, 1, 2, 3) })
        assertTrue("the right answer must not always come first", orders.zip(case.steps).any { (o, s) -> o.first() != s.answer })
    }

    private fun play(firstTryRight: Int): FspCaseRun {
        var r = run()
        case.steps.forEachIndexed { i, s ->
            if (i >= firstTryRight) r = r.pick((0..3).first { it != s.answer })
            r = r.pick(s.answer).next()
        }
        return r
    }

    @Test fun scoreAndPartScoresCountFirstTryRightSteps() {
        val all = play(case.steps.size)
        assertTrue(all.isFinished)
        assertEquals(1.0, all.score, 1e-9)
        assertEquals(mapOf(1 to (16 to 16), 2 to (7 to 7), 3 to (11 to 11)), all.partScores())
        val sixteen = play(16)
        assertEquals(16.0 / 34, sixteen.score, 1e-9)
        assertEquals(mapOf(1 to (16 to 16), 2 to (0 to 7), 3 to (0 to 11)), sixteen.partScores())
    }

    @Test fun gradingFollowsTheScoreNeverTheTime() {
        assertEquals(ReviewGrade.AGAIN, FspGrading.grade(0.79, CardPhase.NEW))
        assertEquals(ReviewGrade.AGAIN, FspGrading.grade(0.79, CardPhase.REVIEW))
        assertEquals(ReviewGrade.GOOD, FspGrading.grade(0.8, CardPhase.NEW))
        assertEquals(ReviewGrade.GOOD, FspGrading.grade(1.0, CardPhase.LEARNING))
        assertEquals(ReviewGrade.GOOD, FspGrading.grade(1.0, CardPhase.RELEARNING))
        assertEquals(ReviewGrade.HARD, FspGrading.grade(0.8, CardPhase.REVIEW))
        assertEquals(ReviewGrade.HARD, FspGrading.grade(0.89, CardPhase.REVIEW))
        assertEquals(ReviewGrade.GOOD, FspGrading.grade(0.9, CardPhase.REVIEW))
        assertEquals(ReviewGrade.GOOD, FspGrading.grade(0.94, CardPhase.REVIEW))
        assertEquals(ReviewGrade.EASY, FspGrading.grade(0.95, CardPhase.REVIEW))
        assertTrue(FspGrading.passed(0.8))
        assertFalse(FspGrading.passed(0.7999))
        // 28 of 34 steps (82 %) is the least that passes; 27 of 34 is 79.4 %
        assertTrue(FspGrading.passed(28.0 / 34))
        assertFalse(FspGrading.passed(27.0 / 34))
    }

    @Test fun differentSeedsGiveDifferentOrders() {
        val a = (0 until case.steps.size).map { FspCaseRun(case, 1).copy(stepIndex = it).order() }
        val b = (0 until case.steps.size).map { FspCaseRun(case, 2).copy(stepIndex = it).order() }
        assertNotEquals(a, b)
    }

    @Test fun aMalformedStepIsRefused() {
        val ok = listOf("a", "b", "c", "d")
        assertThrows(IllegalArgumentException::class.java) { FspStep(1, "S", null, "Q?", ok.take(3), 0, ok, null) }
        assertThrows(IllegalArgumentException::class.java) { FspStep(1, "S", null, "Q?", ok, 0, ok.take(3), null) }
        assertThrows(IllegalArgumentException::class.java) { FspStep(1, "S", null, "Q?", ok, 4, ok, null) }
        assertThrows(IllegalArgumentException::class.java) { FspStep(1, "S", null, "Q?", ok, -1, ok, null) }
    }

    @Test fun aFinishedRunIgnoresEveryMove() {
        val done = play(case.steps.size)
        assertNull(done.step)
        assertTrue(done.order().isEmpty())
        assertSame(done, done.pick(0))
        assertSame(done, done.next())
    }

    @Test fun anUnknownOptionIsIgnored() {
        val r = run()
        assertSame(r, r.pick(4))
        assertSame(r, r.pick(-1))
    }

    @Test fun unknownFieldsInTheFileAreIgnored() {
        val text = """[{"id":"x","area":"A","title":"T","diagnosis":"D","source":"common_FSP","bwReports":0,"patient":"P","extra":1,
            "steps":[{"part":1,"section":"S","question":"Q?","options":["a","b","c","d"],"answer":2,"feedback":["1","2","Richtig","4"],"note":"n"}]}]"""
        val parsed = parseFspCases(text).single()
        assertEquals("x", parsed.id)
        assertNull(parsed.steps.single().situation)
        assertNull(parsed.steps.single().reply)
    }
}

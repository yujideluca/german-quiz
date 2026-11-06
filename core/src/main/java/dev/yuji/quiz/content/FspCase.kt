package dev.yuji.quiz.content

import dev.yuji.quiz.domain.CardPhase
import dev.yuji.quiz.domain.ReviewGrade
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One simulated Fachsprachprüfung (FSP) case (ADR-0014). Every case follows the same template: part 1
 * Arzt-Patienten-Gespräch, part 2 Dokumentation, part 3 Arzt-Arzt-Gespräch with the Fachbegriffe list at its end.
 * The files are written in `tools/data/fsp/` and checked by `tools/check_fsp_cases.py` before they are built.
 */
@Serializable
data class FspCase(
    /** Stable key; the card id is "fsp:<id>". */
    val id: String,
    /** Area of medicine, e.g. "Kardiologie"; the deck keeps the order of the case list. */
    val area: String,
    /** The complaint in plain words; never names the diagnosis (FS-5). */
    val title: String,
    val diagnosis: String,
    /** "reported_BW" or "common_FSP". */
    val source: String,
    val bwReports: Int,
    /** Who the patient is, shown before the first step. */
    val patient: String,
    val steps: List<FspStep>,
)

@Serializable
data class FspStep(
    /** 1 Arzt-Patienten-Gespräch, 2 Dokumentation, 3 Arzt-Arzt-Gespräch. */
    val part: Int,
    val section: String,
    /** What happens before the question, e.g. what the patient says. */
    val situation: String? = null,
    val question: String,
    val options: List<String>,
    /** Index in [options] of the right answer. */
    val answer: Int,
    /** One explanation per option, same order as [options]. */
    val feedback: List<String>,
    /** What the patient or colleague answers once the step is solved. */
    val reply: String? = null,
) {
    init {
        require(options.size == 4 && feedback.size == 4) { "step '$section' needs 4 options and 4 feedback texts" }
        require(answer in options.indices) { "step '$section': answer $answer out of range" }
    }
}

private val caseJson = Json { ignoreUnknownKeys = true }

fun parseFspCases(text: String): List<FspCase> = caseJson.decodeFromString(text)

/** The two "choices" of a case card: the scheduler only learns whether the case was passed. */
const val FSP_PASSED = "bestanden"
const val FSP_FAILED = "nicht bestanden"

/** The share of first-try right steps a case needs to count as a correct answer (FS-4). */
const val FSP_PASS_SCORE = 0.8

/** FS-4: how a finished case is graded. Time spent never matters; only the score and the card's phase. */
object FspGrading {
    private const val EASY_SCORE = 0.95
    private const val HARD_BELOW = 0.9

    fun passed(score: Double): Boolean = score >= FSP_PASS_SCORE

    fun grade(score: Double, phase: CardPhase): ReviewGrade = when {
        !passed(score) -> ReviewGrade.AGAIN
        phase == CardPhase.REVIEW && score >= EASY_SCORE -> ReviewGrade.EASY
        phase == CardPhase.REVIEW && score < HARD_BELOW -> ReviewGrade.HARD
        else -> ReviewGrade.GOOD
    }
}

/**
 * A case being played: immutable, every move returns a copy (FS-3). A wrong pick is remembered and shown; the player
 * picks again until the right answer, and only a first-try right answer counts for the score.
 */
data class FspCaseRun(
    val case: FspCase,
    /** Seeds the answer order of every step, so the order stays put while the player picks. */
    val seed: Long,
    val stepIndex: Int = 0,
    /** Per finished step: was the first pick right? */
    val firstTry: List<Boolean> = emptyList(),
    /** Option indices (in the case file's order) already picked wrongly in the current step. */
    val wrongPicks: Set<Int> = emptySet(),
    /** True once the right answer of the current step was picked; [next] then moves on. */
    val solved: Boolean = false,
) {
    val isFinished: Boolean get() = stepIndex >= case.steps.size
    val step: FspStep? get() = case.steps.getOrNull(stepIndex)

    /** Option indices in the order shown for the current step. */
    fun order(): List<Int> =
        if (isFinished) emptyList() else (0..3).shuffled(Random(seed xor (stepIndex.toLong() * 7919) xor case.id.hashCode().toLong()))

    /** Picks option [option] (an index into the case file's options). Ignored after solving or for a repeated wrong pick. */
    fun pick(option: Int): FspCaseRun {
        val s = step ?: return this
        if (solved || option !in s.options.indices || option in wrongPicks) return this
        return if (option == s.answer) copy(solved = true, firstTry = firstTry + wrongPicks.isEmpty())
        else copy(wrongPicks = wrongPicks + option)
    }

    /** Moves to the next step; ignored until the current one is solved. */
    fun next(): FspCaseRun = if (!solved) this else copy(stepIndex = stepIndex + 1, wrongPicks = emptySet(), solved = false)

    /** Share of steps right at the first pick, over all steps of the case. */
    val score: Double get() = firstTry.count { it }.toDouble() / case.steps.size

    /** Part number -> (first-try right, steps) over the finished steps. */
    fun partScores(): Map<Int, Pair<Int, Int>> =
        case.steps.take(firstTry.size).zip(firstTry).groupBy({ it.first.part }, { it.second })
            .mapValues { (_, r) -> r.count { it } to r.size }.toSortedMap()
}

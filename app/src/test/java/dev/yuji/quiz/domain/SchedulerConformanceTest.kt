package dev.yuji.quiz.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * REQ SP-1 SP-2 SP-3 SP-4 SP-5 GR-1 WB-1
 * Runs the hand-computed vectors in `resources/conformance/scheduler.json`. The web version runs the same file.
 */
class SchedulerConformanceTest {
    private val doc = Json.parseToJsonElement(
        javaClass.getResourceAsStream("/conformance/scheduler.json")!!.bufferedReader().readText(),
    ).jsonObject
    private val t0 = 1_000 * DAY_MILLIS

    private fun start(o: JsonObject): CardProgress {
        val base = SpacedRepetitionScheduler.freshProgress("c")
        if (o["phase"]!!.jsonPrimitive.content == "NEW") return base
        return base.copy(
            phase = CardPhase.valueOf(o["phase"]!!.jsonPrimitive.content),
            intervalDays = o["interval"]!!.jsonPrimitive.int,
            easeFactor = o["ease"]!!.jsonPrimitive.double,
            firstSeenAtMillis = 1,
        )
    }

    @Test fun schedulerReproducesEveryVector() {
        for (sc in doc["scenarios"]!!.jsonArray.map { it.jsonObject }) {
            val name = sc["id"]!!.jsonPrimitive.content
            var p = start(sc["start"]!!.jsonObject)
            for ((i, st) in sc["steps"]!!.jsonArray.map { it.jsonObject }.withIndex()) {
                val at = t0 + st["at"]!!.jsonPrimitive.long * MINUTE_MILLIS
                p = SpacedRepetitionScheduler.scheduleNext(p, ReviewGrade.valueOf(st["grade"]!!.jsonPrimitive.content), at)
                val e = st["expect"]!!.jsonObject
                val where = "$name, step ${i + 1}"
                e["phase"]?.let { assertEquals("$where phase", it.jsonPrimitive.content, p.phase.name) }
                e["interval"]?.let { assertEquals("$where interval", it.jsonPrimitive.int, p.intervalDays) }
                e["dueIn"]?.let { assertEquals("$where due", it.jsonPrimitive.long * MINUTE_MILLIS, p.dueAtMillis - at) }
                e["ease"]?.let { assertEquals("$where ease", it.jsonPrimitive.double, p.easeFactor, 1e-9) }
                e["lapses"]?.let { assertEquals("$where lapses", it.jsonPrimitive.int, p.lapses) }
            }
        }
    }

    @Test fun graderReproducesEveryVector() {
        for (row in doc["grader"]!!.jsonArray.map { it.jsonObject }) {
            val got = AnswerGrader.grade(
                row["correct"]!!.jsonPrimitive.boolean, row["ms"]!!.jsonPrimitive.long, CardPhase.valueOf(row["phase"]!!.jsonPrimitive.content),
            )
            assertEquals("$row", row["grade"]!!.jsonPrimitive.content, got.name)
        }
    }
}

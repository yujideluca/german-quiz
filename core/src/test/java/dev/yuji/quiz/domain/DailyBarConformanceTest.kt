package dev.yuji.quiz.domain

// REQ DG-1 DG-2 DG-5

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hand-computed bar vectors (`resources/conformance/daily_bar.json`); the web version runs the same file. */
class DailyBarConformanceTest {
    private val doc = Json.parseToJsonElement(
        javaClass.getResourceAsStream("/conformance/daily_bar.json")!!.bufferedReader().readText(),
    ).jsonObject

    @Test fun goalSnapsLikeTheVectors() {
        for (row in doc["goalSnap"]!!.jsonArray.map { it.jsonObject })
            assertEquals("goal ${row["in"]}", row["out"]!!.jsonPrimitive.int, clampDailyGoal(row["in"]!!.jsonPrimitive.int))
    }

    @Test fun barMatchesTheVectors() {
        for (row in doc["bar"]!!.jsonArray.map { it.jsonObject }) {
            val d = DailyProgress(row["answered"]!!.jsonPrimitive.int, row["goal"]!!.jsonPrimitive.int)
            val where = "${d.answered}/${d.goal}"
            assertEquals(where, row["fraction"]!!.jsonPrimitive.double.toFloat(), d.fraction, 1e-6f)
            assertEquals(where, row["reached"]!!.jsonPrimitive.boolean, d.reached)
        }
    }
}

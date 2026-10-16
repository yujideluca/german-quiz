package dev.yuji.quiz.domain

import java.time.Instant
import java.time.ZoneId

const val MINUTE_MILLIS = 60_000L
const val DAY_MILLIS = 86_400_000L

/** Injected time source so scheduling is deterministic in tests. */
fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis() = System.currentTimeMillis()
}

/** Local midnight (start of the study day) containing [nowMillis]. */
fun startOfDayMillis(nowMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

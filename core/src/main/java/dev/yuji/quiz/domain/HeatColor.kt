package dev.yuji.quiz.domain

/** Answers per day at which the calendar colour is fully saturated (the largest daily goal). */
const val HEAT_FULL_ANSWERS = MAX_DAILY_GOAL

private const val LIGHT_YELLOW = 0xFFF4B5
private const val ORANGE = 0xFFA53A
private const val RED_ORANGE = 0xE4401C

/** Warm spectrum for a trained day: light yellow (a few answers) -> orange -> orange-red ([HEAT_FULL_ANSWERS]+). */
fun heatColorRgb(answered: Int): Int {
    val t = (answered.coerceAtLeast(0).toFloat() / HEAT_FULL_ANSWERS).coerceIn(0f, 1f)
    return if (t < 0.5f) lerpRgb(LIGHT_YELLOW, ORANGE, t * 2) else lerpRgb(ORANGE, RED_ORANGE, (t - 0.5f) * 2)
}

private fun lerpRgb(from: Int, to: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).toInt().coerceIn(0, 255)
    }
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

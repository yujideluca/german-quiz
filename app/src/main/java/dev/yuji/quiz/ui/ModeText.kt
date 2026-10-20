package dev.yuji.quiz.ui

import androidx.annotation.StringRes
import dev.yuji.quiz.R
import dev.yuji.quiz.domain.GameMode

@StringRes
fun GameMode.titleRes(): Int = when (this) {
    GameMode.ARTICLE -> R.string.mode_article_title
    GameMode.PREPOSITION -> R.string.mode_preposition_title
    GameMode.PLURAL -> R.string.mode_plural_title
    GameMode.PRETERITE -> R.string.mode_preterite_title
    GameMode.ERROR_SPOT -> R.string.mode_errors_title
}

@StringRes
fun GameMode.instructionRes(): Int = when (this) {
    GameMode.ARTICLE -> R.string.mode_article_instruction
    GameMode.PREPOSITION -> R.string.mode_preposition_instruction
    GameMode.PLURAL -> R.string.mode_plural_instruction
    GameMode.PRETERITE -> R.string.mode_preterite_instruction
    GameMode.ERROR_SPOT -> R.string.mode_errors_instruction
}

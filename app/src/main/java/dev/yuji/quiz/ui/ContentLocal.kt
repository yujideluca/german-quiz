package dev.yuji.quiz.ui

import androidx.compose.runtime.compositionLocalOf
import dev.yuji.quiz.content.ContentTranslations

/** Translations of the explanation text in the vocabulary data for the language on screen (none for English). */
val LocalContentTranslations = compositionLocalOf { ContentTranslations.NONE }

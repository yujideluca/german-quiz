package dev.yuji.quiz.domain

enum class GameMode(
    val storageId: String,
    val title: String,
    val instruction: String,
    /** False when the explanation needs reading time: the player taps "Next" instead of waiting 0.5 s. */
    val autoAdvances: Boolean = true,
) {
    ARTICLE("article", "Der · Die · Das", "Choose the correct article"),
    PREPOSITION("preposition", "Prepositions", "Fill in the missing preposition"),
    PLURAL("plural", "Plural forms", "Choose the correct plural form"),
    PRETERITE("preterite", "Präteritum", "Choose the correct Präteritum form"),
    ERROR_SPOT("errors", "Fehler finden", "Tap the part of the sentence that is wrong", autoAdvances = false),
}

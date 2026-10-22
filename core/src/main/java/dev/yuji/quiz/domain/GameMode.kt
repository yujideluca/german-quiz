package dev.yuji.quiz.domain

enum class GameMode(
    val storageId: String,
    /** Developer label for logs; the player sees the localized text from `ui/ModeText.kt`. */
    val label: String,
    /** False when the explanation needs reading time: the player taps "Next" instead of waiting 0.5 s. */
    val autoAdvances: Boolean = true,
) {
    ARTICLE("article", "Article"),
    PREPOSITION("preposition", "Preposition"),
    PLURAL("plural", "Plural"),
    PRETERITE("preterite", "Präteritum"),
    ERROR_SPOT("errors", "Fehler finden", autoAdvances = false),
}

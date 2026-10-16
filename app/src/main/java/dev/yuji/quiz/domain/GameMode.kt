package dev.yuji.quiz.domain

enum class GameMode(val storageId: String, val title: String, val instruction: String) {
    ARTICLE("article", "Der · Die · Das", "Choose the correct article"),
    PREPOSITION("preposition", "Prepositions", "Fill in the missing preposition"),
    PLURAL("plural", "Plural forms", "Choose the correct plural form"),
}

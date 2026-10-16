package dev.yuji.quiz.domain

/**
 * One question in one game mode. Immutable and mode-agnostic: the UI and the scheduler
 * only ever see this shape, so a new mode only needs a new card factory.
 */
data class Card(
    /** Stable across app versions: "<mode>:<key>". Progress is stored under this id. */
    val id: String,
    val mode: GameMode,
    val prompt: String,
    /** Optional second line, e.g. the example sentence with a blank. */
    val context: String?,
    val choices: List<String>,
    val correctChoice: String,
    /** Shown after answering. */
    val explanation: String,
    /** False keeps the canonical order (der/die/das); true shuffles on every presentation. */
    val shuffleChoices: Boolean,
) {
    init {
        require(correctChoice in choices) { "Card $id: correct choice '$correctChoice' not in $choices" }
        require(choices.toSet().size == choices.size) { "Card $id: duplicate choices $choices" }
    }
}

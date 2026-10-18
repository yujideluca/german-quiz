package dev.yuji.quiz.content

import kotlinx.serialization.Serializable

@Serializable
data class NounEntry(
    val article: String,
    val singular: String,
    val plural: String,
    val alsoValidPlurals: List<String> = emptyList(),
)

@Serializable
data class PrepositionEntry(
    /** V verb, A adjective, N noun, E fixed expression. */
    val kind: String,
    val headword: String,
    val preposition: String,
    /** A = Akkusativ, D = Dativ. */
    val case: String,
    /** Sentence containing exactly one "___". */
    val sentence: String,
    val alsoValidPrepositions: List<String> = emptyList(),
    /** Says which meaning is asked when the headword takes several prepositions; shown under the headword. */
    val hint: String? = null,
)

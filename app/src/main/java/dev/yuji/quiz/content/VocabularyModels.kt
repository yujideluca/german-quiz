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

@Serializable
data class VerbEntry(
    val infinitive: String,
    /** Präteritum, 3rd person singular (er/sie/es). */
    val preterite: String,
    /** Perfekt, 3rd person singular, with auxiliary: "ist gegangen". */
    val perfect: String,
    /** Präsens, 3rd person singular. */
    val present: String,
)

/** A B2 writing sentence (forum post or formal e-mail) split into tappable chunks, exactly one of which is wrong. */
@Serializable
data class ErrorSentenceEntry(
    /** Stable key, e.g. "F01"; the card id is "error:<id>". */
    val id: String,
    /** "Forumsbeitrag" or "Formelle E-Mail". */
    val register: String,
    /** Human-readable error category, e.g. "Preposition and case". */
    val errorType: String,
    val segments: List<String>,
    /** Index in [segments] of the chunk that contains the error. */
    val errorIndex: Int,
    /** What the wrong chunk should read instead. */
    val fix: String,
    val rule: String,
    /** The Redemittel or fixed phrase the sentence practises. */
    val expression: String,
)

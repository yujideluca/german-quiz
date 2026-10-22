package dev.yuji.quiz.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Translations of the English explanation text that lives in the vocabulary files (the preposition hints and the
 * "Fehler finden" error types and rules), keyed by the English original. The German questions, sentences and answer
 * options are never translated. Anything without a translation is shown as written, so a missing entry degrades to
 * English instead of to a blank.
 */
@Serializable
data class ContentTranslations(
    val hints: Map<String, String> = emptyMap(),
    val errorTypes: Map<String, String> = emptyMap(),
    val rules: Map<String, String> = emptyMap(),
) {
    fun hint(text: String): String = hints[text] ?: text

    /**
     * The explanation of an answered card. Only the part after the first blank line (the error type and rule of a
     * "Fehler finden" card) is translated; the corrected sentence in front of it is German and stays.
     */
    fun explanation(text: String): String {
        val split = text.indexOf("\n\n")
        if (split < 0) return text
        val head = text.substring(0, split + 2)
        var tail = text.substring(split + 2)
        rules.entries.firstOrNull { tail.contains(it.key) }?.let { tail = tail.replace(it.key, it.value) }
        errorTypes.entries.firstOrNull { tail.startsWith(it.key + ": ") }?.let { tail = it.value + tail.substring(it.key.length) }
        return head + tail
    }

    companion object {
        val NONE = ContentTranslations()
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): ContentTranslations = json.decodeFromString(text)
    }
}

package dev.yuji.quiz.data

import java.util.Locale

/**
 * Language of the app's own text (menus, buttons, messages, explanations). Questions and answer options are German
 * vocabulary and never change. [SYSTEM] follows the phone and is the default; the others override it.
 */
enum class AppLanguage(val storageId: String, val tag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    GERMAN("de", "de"),
    PORTUGUESE("pt", "pt-BR"),
    SPANISH("es", "es");

    /** The language actually shown: [SYSTEM] resolves to the phone's language, or English when it is not offered here. */
    fun effective(systemLanguageCode: String): AppLanguage =
        if (this != SYSTEM) this else entries.firstOrNull { it != SYSTEM && it.storageId == systemLanguageCode } ?: ENGLISH

    fun locale(systemLocale: Locale): Locale = tag?.let(Locale::forLanguageTag) ?: systemLocale

    companion object {
        fun fromStorageId(id: String?): AppLanguage = entries.firstOrNull { it.storageId == id } ?: SYSTEM
    }
}

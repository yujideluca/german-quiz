package dev.yuji.quiz.data

import android.content.Context
import dev.yuji.quiz.content.ContentTranslations

/** Loads `assets/i18n/<language>.json`; English (the language of the original text) and unknown languages have none. */
fun loadContentTranslations(context: Context, language: AppLanguage): ContentTranslations {
    if (language == AppLanguage.ENGLISH || language == AppLanguage.SYSTEM) return ContentTranslations.NONE
    return runCatching {
        ContentTranslations.parse(context.assets.open("i18n/${language.storageId}.json").bufferedReader().use { it.readText() })
    }.getOrDefault(ContentTranslations.NONE)
}

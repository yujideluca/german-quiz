package dev.yuji.quiz.data

import dev.yuji.quiz.content.ContentFiles
import dev.yuji.quiz.content.ContentTranslations

/** Loads `i18n/<language>.json` of the pool in use; English (the language of the original text) and unknown languages have none. */
fun loadContentTranslations(files: ContentFiles, language: AppLanguage): ContentTranslations {
    if (language == AppLanguage.ENGLISH || language == AppLanguage.SYSTEM) return ContentTranslations.NONE
    return runCatching { ContentTranslations.parse(files.read("i18n/${language.storageId}.json")) }.getOrDefault(ContentTranslations.NONE)
}

package dev.yuji.quiz.android

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import dev.yuji.quiz.data.AppLanguage
import java.util.Locale

/** The phone's own language list, unaffected by whatever this app chose for itself. */
fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

/**
 * A context whose string resources, date formats and plurals follow [language]. [AppLanguage.SYSTEM] returns the
 * context unchanged (Android already picks the phone's language and falls back to English). The JVM default locale
 * is set too, so `Locale.getDefault()` formatting matches what is on screen.
 */
fun Context.withLanguage(language: AppLanguage): Context {
    val locale = language.locale(systemLocale())
    Locale.setDefault(locale)
    if (language == AppLanguage.SYSTEM) return this
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

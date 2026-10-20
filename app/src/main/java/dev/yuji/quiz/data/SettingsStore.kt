package dev.yuji.quiz.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface SettingsStore {
    var themeMode: ThemeMode
    var language: AppLanguage
}

class InMemorySettingsStore(
    override var themeMode: ThemeMode = ThemeMode.SYSTEM,
    override var language: AppLanguage = AppLanguage.SYSTEM,
) : SettingsStore

class SharedPreferencesSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    override var language: AppLanguage
        get() = AppLanguage.fromStorageId(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.storageId).apply()

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LANGUAGE = "language"
    }
}

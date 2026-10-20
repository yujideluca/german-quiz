package dev.yuji.quiz.data

// REQ LG-1

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun `system follows the phone when its language is offered`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.SYSTEM.effective("de"))
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.SYSTEM.effective("pt"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.SYSTEM.effective("es"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.effective("en"))
    }

    @Test fun `system falls back to English for a language the app does not offer`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.effective("fr"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.effective(""))
    }

    @Test fun `an explicit choice wins over the phone`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.GERMAN.effective("pt"))
    }

    @Test fun `stored ids round trip and unknown ids mean system`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromStorageId(it.storageId)) }
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageId(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageId("klingon"))
    }

    @Test fun `locale is the phone's for system and the chosen one otherwise`() {
        assertEquals(Locale.JAPANESE, AppLanguage.SYSTEM.locale(Locale.JAPANESE))
        assertEquals("pt", AppLanguage.PORTUGUESE.locale(Locale.JAPANESE).language)
        assertEquals("BR", AppLanguage.PORTUGUESE.locale(Locale.JAPANESE).country)
    }
}

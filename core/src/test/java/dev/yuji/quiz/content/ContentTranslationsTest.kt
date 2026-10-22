package dev.yuji.quiz.content

// REQ LG-1

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTranslationsTest {
    private val vocab = File("../app/src/main/assets/vocab")
    private fun rows(name: String) = Json.parseToJsonElement(File(vocab, name).readText()) as JsonArray
    private fun translations(lang: String) = ContentTranslations.parse(File("../app/src/main/assets/i18n/$lang.json").readText())

    private val languages = listOf("de", "pt", "es")

    @Test fun `every error type, rule and hint in the data has a translation in every language`() {
        val types = rows("errors.json").map { it.jsonObject["errorType"]!!.jsonPrimitive.content }.toSet()
        val rules = rows("errors.json").map { it.jsonObject["rule"]!!.jsonPrimitive.content }.toSet()
        val hints = rows("prepositions.json").mapNotNull { it.jsonObject["hint"]?.jsonPrimitive?.content }.toSet()
        languages.forEach { lang ->
            val t = translations(lang)
            assertEquals("$lang types", emptySet<String>(), types - t.errorTypes.keys)
            assertEquals("$lang rules", emptySet<String>(), rules - t.rules.keys)
            assertEquals("$lang hints", emptySet<String>(), hints - t.hints.keys)
            assertTrue("$lang has blank entries", (t.rules.values + t.errorTypes.values + t.hints.values).none { it.isBlank() })
        }
    }

    private val explanation = "✓ Meiner Ansicht nach wird Plastik benutzt\n\n" +
        "Passive: werden, not sein: An action or process takes werden + Partizip II (\"wird benutzt\"); " +
        "sein + Partizip II only describes a finished state.\nRedemittel: Meiner Ansicht nach"

    @Test fun `an error explanation keeps the German sentence and expression and translates type and rule`() {
        val shown = translations("pt").explanation(explanation)
        assertTrue(shown.startsWith("✓ Meiner Ansicht nach wird Plastik benutzt\n\n"))
        assertTrue("Passivo: werden, não sein: " in shown)
        assertTrue("Uma ação ou processo usa werden + Partizip II" in shown)
        assertTrue(shown.endsWith("\nRedemittel: Meiner Ansicht nach"))
        assertNotEquals(explanation, shown)
    }

    @Test fun `text without a translation is shown unchanged`() {
        assertEquals(explanation, ContentTranslations.NONE.explanation(explanation))
        assertEquals("der Tisch  ·  Plural: die Tische", translations("de").explanation("der Tisch  ·  Plural: die Tische"))
        assertEquals("unknown hint", translations("es").hint("unknown hint"))
    }

    @Test fun `hints are translated`() {
        assertEquals("über ein Thema sprechen", translations("de").hint("talk about a topic"))
    }
}

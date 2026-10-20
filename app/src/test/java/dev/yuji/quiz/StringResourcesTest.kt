package dev.yuji.quiz

// REQ LG-1

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Every language file offers the same strings with the same format placeholders as the English default. */
class StringResourcesTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("%\\d+\\$[sd]")

    private fun load(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        val out = mutableMapOf<String, String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) (strings.item(i) as Element).let { out[it.getAttribute("name")] = it.textContent }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val p = plurals.item(i) as Element
            val items = p.getElementsByTagName("item")
            for (j in 0 until items.length) (items.item(j) as Element).let { out["${p.getAttribute("name")}#${it.getAttribute("quantity")}"] = it.textContent }
        }
        return out
    }

    private val english = load("values")
    private val translated = listOf("values-de", "values-pt", "values-es")

    @Test fun `every translated string exists in every language`() {
        translated.forEach { folder ->
            val other = load(folder)
            val missing = english.keys.filterNot { it.startsWith("language_name_") } - other.keys
            assertEquals("$folder is missing strings", emptyList<String>(), missing)
            assertEquals("$folder has strings English lacks", emptySet<String>(), other.keys - english.keys)
        }
    }

    @Test fun `placeholders match the English text`() {
        translated.forEach { folder ->
            val other = load(folder)
            english.forEach { (key, en) ->
                val tr = other[key] ?: return@forEach
                assertEquals("$folder/$key", placeholder.findAll(en).map { it.value }.toSet(), placeholder.findAll(tr).map { it.value }.toSet())
            }
        }
    }

    @Test fun `no string is blank`() {
        (translated + "values").forEach { folder -> assertTrue(folder, load(folder).values.none { it.isBlank() }) }
    }
}

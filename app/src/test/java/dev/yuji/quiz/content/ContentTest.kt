package dev.yuji.quiz.content

import dev.yuji.quiz.data.parseNouns
import dev.yuji.quiz.data.parsePrepositions
import dev.yuji.quiz.domain.GameMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTest {
    private val nouns = parseNouns(File("src/main/assets/vocab/nouns.json").readText())
    private val preps = parsePrepositions(File("src/main/assets/vocab/prepositions.json").readText())
    private val deck = CardFactory.buildDeck(nouns, preps)

    @Test fun poolHasReasonableSize() {
        assertTrue(nouns.size >= 250)
        assertTrue(preps.size >= 150)
    }

    @Test fun everyModeHasCards() = GameMode.entries.forEach { assertTrue("$it", deck.getValue(it).isNotEmpty()) }

    @Test fun cardIdsAreUniqueAcrossTheDeck() {
        val ids = deck.values.flatten().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun articleCardsOfferExactlyDerDieDas() {
        deck.getValue(GameMode.ARTICLE).forEach { assertEquals(listOf("der", "die", "das"), it.choices) }
    }

    @Test fun pluralCardsHaveFourDistinctChoicesIncludingTheCorrectOne() {
        deck.getValue(GameMode.PLURAL).forEach {
            assertEquals(it.id, 4, it.choices.size)
            assertTrue(it.correctChoice in it.choices)
        }
    }

    @Test fun pluralDistractorsNeverContainAnAcceptedPlural() = nouns.forEach { n ->
        val accepted = n.alsoValidPlurals + n.plural
        DistractorFactory.pluralDistractors(n).forEach { assertFalse("${n.singular}: $it", it in accepted) }
    }

    @Test fun everyHeadwordWithSeveralPrepositionsCarriesAHintOnEachCard() {
        val groups = preps.groupBy { it.kind to it.headword }.filterValues { g -> g.map { it.preposition }.toSet().size > 1 }
        assertTrue("expected the known ambiguous verbs", groups.keys.map { it.second }.containsAll(listOf("sich freuen", "sprechen", "halten")))
        groups.values.flatten().forEach { assertFalse("${it.headword} ${it.preposition} lacks a hint", it.hint.isNullOrBlank()) }
        // the hint reaches the card, and unambiguous verbs stay clean
        val freuen = preps.first { it.headword == "sich freuen" && it.preposition == "auf" }
        assertEquals(freuen.hint, CardFactory.prepositionCard(freuen).hint)
        assertTrue(CardFactory.prepositionCard(freuen).hint!!.contains("future"))
        assertEquals(null, CardFactory.prepositionCard(preps.first { it.headword == "warten" }).hint)
    }

    @Test fun hintedVerbsDoNotAcceptTheOtherMeaningAsAlsoValid() {
        val auf = preps.first { it.headword == "sich freuen" && it.preposition == "auf" }
        assertTrue(auf.alsoValidPrepositions.isEmpty())
        val kaempfen = preps.first { it.headword == "kämpfen" && it.preposition == "für" }
        assertFalse("gegen" in kaempfen.alsoValidPrepositions)
    }

    @Test fun prepositionDistractorsNeverContainValidAnswers() = preps.forEach { p ->
        val d = DistractorFactory.prepositionDistractors(p)
        assertEquals(3, d.size)
        assertFalse(p.preposition in d)
        assertTrue(d.none { it in p.alsoValidPrepositions })
    }

    @Test fun distractorsAreDeterministic() {
        val n = nouns.first()
        assertEquals(DistractorFactory.pluralDistractors(n), DistractorFactory.pluralDistractors(n))
    }

    @Test fun umlautRules() {
        assertEquals("Häus", DistractorFactory.withUmlaut("Haus"))
        assertEquals("Bäum", DistractorFactory.withUmlaut("Baum"))
        assertEquals("Händ", DistractorFactory.withUmlaut("Hand"))
        assertEquals("Lehrer", DistractorFactory.withUmlaut("Lehrer"))
    }

    @Test fun everySentenceHasExactlyOneBlank() = preps.forEach { assertEquals(it.sentence, 1, it.sentence.split("___").size - 1) }
}

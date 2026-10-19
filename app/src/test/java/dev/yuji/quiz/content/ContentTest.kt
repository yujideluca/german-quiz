package dev.yuji.quiz.content

import dev.yuji.quiz.data.parseErrorSentences
import dev.yuji.quiz.data.parseNouns
import dev.yuji.quiz.data.parsePrepositions
import dev.yuji.quiz.data.parseVerbs
import dev.yuji.quiz.domain.GameMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTest {
    private val nouns = parseNouns(File("src/main/assets/vocab/nouns.json").readText())
    private val preps = parsePrepositions(File("src/main/assets/vocab/prepositions.json").readText())
    private val verbs = parseVerbs(File("src/main/assets/vocab/verbs.json").readText())
    private val errors = parseErrorSentences(File("src/main/assets/vocab/errors.json").readText())
    private val deck = CardFactory.buildDeck(nouns, preps, verbs, errors)

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

    @Test fun preteriteCardsHaveFourDistinctChoicesAndTheRegularisedMistake() = verbs.forEach { v ->
        val card = CardFactory.preteriteCard(v)
        assertEquals(card.id, 4, card.choices.size)
        assertEquals(v.preterite, card.correctChoice)
        val weak = DistractorFactory.weakPreterite(v.infinitive)
        if (weak != v.preterite) assertTrue("${v.infinitive}: $weak missing", weak in card.choices)
    }

    @Test fun preteriteDistractorsAreOtherFormsOfTheSameVerb() = verbs.forEach { v ->
        val forms = setOf(DistractorFactory.weakPreterite(v.infinitive), v.perfect.substringAfter(' '), v.present, v.infinitive, "ge" + v.perfect.substringAfter(' '))
        DistractorFactory.preteriteDistractors(v).forEach {
            assertTrue("${v.infinitive}: $it", it in forms)
            assertFalse("${v.infinitive}: $it equals the answer", it == v.preterite)
        }
    }

    @Test fun weakPreteriteFollowsTheRegularPattern() {
        assertEquals("gehte", DistractorFactory.weakPreterite("gehen"))
        assertEquals("findete", DistractorFactory.weakPreterite("finden"))
        assertEquals("arbeitete", DistractorFactory.weakPreterite("arbeiten"))
        assertEquals("nehmte", DistractorFactory.weakPreterite("nehmen"))
        assertEquals("kommte", DistractorFactory.weakPreterite("kommen"))
        assertEquals("machte", DistractorFactory.weakPreterite("machen"))
    }

    @Test fun preteritePoolHasReasonableSize() = assertTrue(verbs.size >= 100)

    @Test fun errorSentencesAreBigEnoughAndCoverBothRegisters() {
        assertTrue(errors.size >= 100)
        assertEquals(setOf("Forumsbeitrag", "Formelle E-Mail"), errors.map { it.register }.toSet())
        assertTrue("every error type should have several sentences", errors.groupBy { it.errorType }.values.all { it.size >= 3 })
    }

    @Test fun errorCardOffersTheChunksInOrderAndTheWrongChunkIsTheAnswer() {
        deck.getValue(GameMode.ERROR_SPOT).forEach { card ->
            val entry = errors.first { "error:${it.id}" == card.id }
            assertEquals(card.id, entry.segments, card.choices)
            assertEquals(card.id, entry.segments[entry.errorIndex], card.correctChoice)
            assertFalse("chunks keep the sentence order", card.shuffleChoices)
        }
    }

    @Test fun errorCardExplanationShowsTheCorrectedSentenceRuleAndExpression() {
        val entry = errors.first { it.id == "F01" }
        val card = CardFactory.errorCard(entry)
        assertTrue(card.explanation, card.explanation.startsWith("✓ Meiner Ansicht nach wird Plastik heute viel zu oft benutzt"))
        assertTrue(card.explanation.contains(entry.rule))
        assertTrue(card.explanation.contains("Redemittel: " + entry.expression))
        assertFalse("the corrected sentence must not repeat the wrong chunk", card.explanation.substringBefore("\n").contains(card.correctChoice))
    }

    @Test fun everyErrorSentenceHasADistinctFixAndExpression() = errors.forEach {
        assertTrue(it.id, it.fix.isNotBlank() && it.fix != it.segments[it.errorIndex])
        assertTrue(it.id, it.expression.isNotBlank() && it.rule.isNotBlank())
        assertTrue(it.id, it.segments.size in 3..5)
    }

    @Test fun errorModeWaitsForANextTapWhileTheOtherModesAdvanceByThemselves() {
        assertFalse(GameMode.ERROR_SPOT.autoAdvances)
        assertTrue(GameMode.entries.filter { it != GameMode.ERROR_SPOT }.all { it.autoAdvances })
    }

    @Test fun errorDeckIsMixedSoTheFirstSentencesAreNotOneErrorType() {
        val firstTypes = deck.getValue(GameMode.ERROR_SPOT).take(10).map { c -> errors.first { "error:${it.id}" == c.id }.errorType }
        assertTrue(firstTypes.toString(), firstTypes.toSet().size >= 4)
    }
}

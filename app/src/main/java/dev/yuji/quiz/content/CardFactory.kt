package dev.yuji.quiz.content

import dev.yuji.quiz.domain.Card
import dev.yuji.quiz.domain.GameMode
import kotlin.random.Random

/** Turns vocabulary entries into cards. One function per game mode. */
object CardFactory {
    private val ARTICLES = listOf("der", "die", "das")

    fun articleCard(noun: NounEntry) = Card(
        id = "article:${noun.singular}",
        mode = GameMode.ARTICLE,
        prompt = noun.singular,
        context = null,
        choices = ARTICLES,
        correctChoice = noun.article,
        explanation = "${noun.article} ${noun.singular}  ·  Plural: die ${noun.plural}",
        shuffleChoices = false,
    )

    fun pluralCard(noun: NounEntry): Card {
        val correct = noun.plural
        val choices = (listOf(correct) + DistractorFactory.pluralDistractors(noun))
            .shuffled(Random(("plural-order:" + noun.singular).hashCode().toLong()))
        return Card(
            id = "plural:${noun.singular}",
            mode = GameMode.PLURAL,
            prompt = "${noun.article} ${noun.singular}",
            context = "Plural: die ___",
            choices = choices,
            correctChoice = correct,
            explanation = "${noun.article} ${noun.singular}  →  die ${noun.plural}",
            shuffleChoices = true,
        )
    }

    fun prepositionCard(entry: PrepositionEntry): Card {
        val caseName = if (entry.case == "A") "Akkusativ" else "Dativ"
        val choices = listOf(entry.preposition) + DistractorFactory.prepositionDistractors(entry)
        return Card(
            id = "preposition:${entry.headword}:${entry.preposition}",
            mode = GameMode.PREPOSITION,
            prompt = entry.headword,
            context = entry.sentence,
            choices = choices,
            correctChoice = entry.preposition,
            explanation = "${entry.headword} + ${entry.preposition}  ($caseName)",
            shuffleChoices = true,
            hint = entry.hint,
        )
    }

    fun buildDeck(nouns: List<NounEntry>, prepositions: List<PrepositionEntry>): Map<GameMode, List<Card>> = mapOf(
        GameMode.ARTICLE to nouns.map(::articleCard),
        GameMode.PLURAL to nouns.map(::pluralCard),
        GameMode.PREPOSITION to prepositions.map(::prepositionCard),
    )
}

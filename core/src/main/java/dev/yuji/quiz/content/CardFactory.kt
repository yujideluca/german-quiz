package dev.yuji.quiz.content

import dev.yuji.quiz.domain.Card
import dev.yuji.quiz.domain.GameMode
import kotlin.random.Random

/** Turns vocabulary entries into cards. One function per game mode. */
object CardFactory {
    private val ARTICLES = listOf("der", "die", "das")
    private const val ERROR_DECK_SEED = 2026L

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

    fun preteriteCard(verb: VerbEntry): Card {
        val choices = (listOf(verb.preterite) + DistractorFactory.preteriteDistractors(verb))
            .shuffled(Random(("preterite-order:" + verb.infinitive).hashCode().toLong()))
        return Card(
            id = "preterite:${verb.infinitive}",
            mode = GameMode.PRETERITE,
            prompt = verb.infinitive,
            context = "Präteritum: er/sie/es ___",
            choices = choices,
            correctChoice = verb.preterite,
            explanation = "${verb.infinitive}  –  ${verb.preterite}  –  ${verb.perfect}",
            shuffleChoices = true,
        )
    }

    fun errorCard(entry: ErrorSentenceEntry): Card {
        val wrong = entry.segments[entry.errorIndex]
        val corrected = entry.segments.mapIndexed { i, chunk -> if (i == entry.errorIndex) entry.fix else chunk }
        return Card(
            id = "error:${entry.id}",
            mode = GameMode.ERROR_SPOT,
            prompt = entry.register,
            context = entry.segments.joinToString(" "),
            choices = entry.segments,
            correctChoice = wrong,
            explanation = "✓ ${corrected.joinToString(" ")}\n\n${entry.errorType}: ${entry.rule}\nRedemittel: ${entry.expression}",
            shuffleChoices = false,
        )
    }

    fun buildDeck(
        nouns: List<NounEntry>,
        prepositions: List<PrepositionEntry>,
        verbs: List<VerbEntry> = emptyList(),
        errorSentences: List<ErrorSentenceEntry> = emptyList(),
    ): Map<GameMode, List<Card>> = mapOf(
        GameMode.ARTICLE to nouns.map(::articleCard),
        GameMode.PLURAL to nouns.map(::pluralCard),
        GameMode.PREPOSITION to prepositions.map(::prepositionCard),
        GameMode.PRETERITE to verbs.map(::preteriteCard),
        // Shuffled once (fixed seed) so new sentences do not arrive grouped by error type or register.
        GameMode.ERROR_SPOT to errorSentences.shuffled(Random(ERROR_DECK_SEED)).map(::errorCard),
    )
}

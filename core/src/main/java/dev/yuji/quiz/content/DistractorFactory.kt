package dev.yuji.quiz.content

import kotlin.random.Random

/** Builds believable wrong answers. Deterministic: the same entry always gets the same distractors. */
object DistractorFactory {

    val PREPOSITION_POOL = listOf(
        "auf", "an", "für", "über", "von", "mit", "bei", "zu", "nach", "um", "vor", "aus", "in", "gegen", "unter",
    )

    fun prepositionDistractors(entry: PrepositionEntry, count: Int = 3): List<String> {
        val excluded = setOf(entry.preposition) + entry.alsoValidPrepositions
        val candidates = PREPOSITION_POOL.filter { it !in excluded }
        return candidates.shuffled(Random(seedOf("prep:${entry.headword}:${entry.preposition}"))).take(count)
    }

    fun pluralDistractors(noun: NounEntry, count: Int = 3): List<String> {
        val excluded = (noun.alsoValidPlurals + noun.plural).toSet()
        val s = noun.singular
        val umlauted = withUmlaut(s)
        val primary = buildList {
            if (s.endsWith("e")) {
                add(s + "n"); add(s + "s"); add(s); add(umlauted)
            } else {
                add(s + "e"); add(s + "en"); add(s + "er"); add(s)
                if (s.last().lowercaseChar() in "aiouy") add(s + "s") // -s plurals follow vowels only
                if (umlauted != s) { add(umlauted + "e"); add(umlauted + "er"); add(umlauted) }
            }
        }
        val fallback = listOf(s + "en", s + "er", s + "e", s + "s")
        val ordered = primary.distinct().filter { it !in excluded && it.isNotBlank() }
            .shuffled(Random(seedOf("plural:$s")))
        val extra = fallback.filter { it !in excluded && it !in ordered }
        return (ordered + extra).take(count)
    }

    /**
     * Wrong Präteritum forms a learner might produce. The regularised weak form ("gehte") comes first
     * because it is the typical mistake; the rest are other forms of the same verb (Partizip II,
     * Präsens, infinitive, ge-prefixed participle), which are never a valid er/sie/es Präteritum.
     */
    fun preteriteDistractors(verb: VerbEntry, count: Int = 3): List<String> {
        val participle = verb.perfect.substringAfter(' ')
        val others = listOf(participle, verb.present, verb.infinitive)
            .filter { it != verb.preterite }.distinct()
            .shuffled(Random(seedOf("preterite:${verb.infinitive}")))
        // Fills the gap when forms coincide (telefoniert is both Präsens and Partizip II).
        val spare = "ge$participle"
        val weak = weakPreterite(verb.infinitive).takeIf { it != verb.preterite }
        return (listOfNotNull(weak) + others + spare).distinct().take(count)
    }

    /** The form a learner would build by treating the verb as regular: gehen -> gehte, finden -> findete. */
    internal fun weakPreterite(infinitive: String): String {
        val stem = if (infinitive.endsWith("en")) infinitive.dropLast(2) else infinitive.dropLast(1)
        val needsE = stem.last() in "td" ||
            (stem.length > 1 && stem.last() in "mn" && stem[stem.length - 2] != stem.last() && stem[stem.length - 2] !in "aeiouäöülrh")
        return stem + if (needsE) "ete" else "te"
    }

    /** Adds an umlaut to the last umlaut-able vowel: Haus -> Häus, Hand -> Händ, Baum -> Bäum. */
    internal fun withUmlaut(word: String): String {
        val au = word.lastIndexOf("au")
        if (au >= 0) return word.substring(0, au) + "äu" + word.substring(au + 2)
        val i = word.indexOfLast { it in "aouAOU" }
        if (i < 0) return word
        val mapped = when (word[i]) {
            'a' -> 'ä'; 'o' -> 'ö'; 'u' -> 'ü'; 'A' -> 'Ä'; 'O' -> 'Ö'; else -> 'Ü'
        }
        return word.substring(0, i) + mapped + word.substring(i + 1)
    }

    private fun seedOf(key: String): Long = key.fold(1125899906842597L) { h, c -> 31 * h + c.code }
}

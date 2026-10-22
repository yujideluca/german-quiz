package dev.yuji.quiz.content

// REQ CT-1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Optional fields may be missing from the generated JSON; the parsers must fill in the documented defaults. */
class VocabularyParsersTest {
    @Test fun optionalNounAndPrepositionFieldsDefault() {
        val noun = parseNouns("""[{"article":"der","singular":"Tisch","plural":"Tische"}]""").single()
        assertEquals(NounEntry("der", "Tisch", "Tische"), noun)
        assertEquals(emptyList<String>(), noun.alsoValidPlurals)

        val prep = parsePrepositions(
            """[{"kind":"V","headword":"warten","preposition":"auf","case":"A","sentence":"Ich warte ___ dich.","extra":1}]""",
        ).single()
        assertEquals(PrepositionEntry("V", "warten", "auf", "A", "Ich warte ___ dich."), prep)
        assertNull(prep.hint)
    }

    @Test fun verbRowsKeepEveryForm() {
        val verb = parseVerbs("""[{"infinitive":"gehen","preterite":"ging","perfect":"ist gegangen","present":"geht"}]""").single()
        assertEquals(VerbEntry("gehen", "ging", "ist gegangen", "geht"), verb)
    }
}

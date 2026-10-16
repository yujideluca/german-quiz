package dev.yuji.quiz.domain

import java.text.Normalizer

enum class SuspensionDuration(val millis: Long?) {
    ONE_WEEK(7 * DAY_MILLIS),
    ONE_MONTH(30 * DAY_MILLIS),
    /** Shown to the user as "removed". */
    PERMANENT(null),
}

enum class CardStatus { SUSPENDED, REMOVED }

/** A card the user took out of the pool, with enough data to show and restore it. */
data class ManagedCard(val card: Card, val status: CardStatus, val suspendedUntilMillis: Long)

data class ManagedCounts(val suspended: Int, val removed: Int)

fun CardProgress.isAvailableAt(nowMillis: Long): Boolean = !removed && suspendedUntilMillis <= nowMillis

fun CardProgress.statusAt(nowMillis: Long): CardStatus? = when {
    removed -> CardStatus.REMOVED
    suspendedUntilMillis > nowMillis -> CardStatus.SUSPENDED
    else -> null
}

fun CardProgress.withSuspension(duration: SuspensionDuration, nowMillis: Long): CardProgress =
    when (val ms = duration.millis) {
        null -> copy(removed = true, suspendedUntilMillis = 0L)
        else -> copy(removed = false, suspendedUntilMillis = nowMillis + ms)
    }

/** Back in the pool; spaced-repetition state is kept untouched. */
fun CardProgress.restored(): CardProgress = copy(removed = false, suspendedUntilMillis = 0L)

/** Lower-cases and strips diacritics so "stuhle" finds "Stühle". */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

/** Case- and umlaut-insensitive search over prompt, sentence, explanation and id. */
fun searchManagedCards(items: List<ManagedCard>, query: String): List<ManagedCard> {
    val q = foldForSearch(query)
    if (q.isEmpty()) return items
    return items.filter { m ->
        listOfNotNull(m.card.prompt, m.card.context, m.card.explanation, m.card.id)
            .any { foldForSearch(it).contains(q) }
    }
}

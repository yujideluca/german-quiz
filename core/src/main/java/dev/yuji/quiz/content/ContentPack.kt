package dev.yuji.quiz.content

import dev.yuji.quiz.domain.Card
import dev.yuji.quiz.domain.GameMode
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The question pool as one versioned set of files: the vocabulary (`vocab/`) and the translations of its English
 * prose (`i18n/`). The app ships one pack in its assets and can download a newer one from the update feed
 * (`content/` in the feed repo), so new questions arrive without a new APK (ADR-0012).
 */

/** The pack format this app reads. A pack with another schema needs a different app version and is ignored. */
const val CONTENT_SCHEMA = 1

/** Every file a pack must contain, as paths relative to the pack's root. */
val CONTENT_FILES = listOf(
    "vocab/nouns.json", "vocab/prepositions.json", "vocab/verbs.json", "vocab/errors.json",
    "i18n/de.json", "i18n/pt.json", "i18n/es.json",
)

const val CONTENT_MANIFEST_FILE = "content.json"

/** `content.json`: which pack this is, and the SHA-256 of each of its files. */
@Serializable
data class ContentManifest(
    val schema: Int,
    val contentVersion: Int,
    val files: Map<String, String>,
    val commit: String? = null,
)

private val manifestJson = Json { ignoreUnknownKeys = true }

fun parseContentManifest(text: String): ContentManifest? =
    runCatching { manifestJson.decodeFromString<ContentManifest>(text) }.getOrNull()

/** Reads one file of a pack by its relative path (assets, a folder on disk, or bytes in memory). */
fun interface ContentFiles {
    fun read(path: String): String
}

fun buildDeckFrom(files: ContentFiles): Map<GameMode, List<Card>> = CardFactory.buildDeck(
    parseNouns(files.read("vocab/nouns.json")),
    parsePrepositions(files.read("vocab/prepositions.json")),
    parseVerbs(files.read("vocab/verbs.json")),
    parseErrorSentences(files.read("vocab/errors.json")),
)

/** True when [remote] is worth downloading: a format this app reads and newer than the pack in use. */
fun shouldDownloadContent(remote: ContentManifest, activeVersion: Int, supportedSchema: Int = CONTENT_SCHEMA): Boolean =
    remote.schema == supportedSchema && remote.contentVersion > activeVersion

/** True when a downloaded pack replaces the one built into the app; otherwise the built-in pack is used. */
fun prefersDownloaded(bundled: ContentManifest, downloaded: ContentManifest?, supportedSchema: Int = CONTENT_SCHEMA): Boolean =
    downloaded != null && downloaded.schema == supportedSchema && downloaded.contentVersion > bundled.contentVersion

/** Why a downloaded pack was refused. */
enum class ContentProblem { UNSUPPORTED_SCHEMA, MISSING_FILE, CHECKSUM_MISMATCH, UNREADABLE, EMPTY_MODE }

sealed interface ContentCheck {
    data class Valid(val deck: Map<GameMode, List<Card>>) : ContentCheck
    data class Invalid(val problem: ContentProblem, val detail: String) : ContentCheck
}

/**
 * Accepts a pack only when it is complete and usable: the schema is [supportedSchema], every file of [CONTENT_FILES]
 * is listed and matches its checksum, the vocabulary and translations parse, and every mode has at least one card.
 * Anything less is refused, so a bad download can never shrink or break the pool in use.
 */
fun validateContentPack(
    manifest: ContentManifest,
    bytes: Map<String, ByteArray>,
    supportedSchema: Int = CONTENT_SCHEMA,
): ContentCheck {
    if (manifest.schema != supportedSchema) return ContentCheck.Invalid(ContentProblem.UNSUPPORTED_SCHEMA, "schema ${manifest.schema}")
    for (path in CONTENT_FILES) {
        val expected = manifest.files[path] ?: return ContentCheck.Invalid(ContentProblem.MISSING_FILE, "$path not in manifest")
        val data = bytes[path] ?: return ContentCheck.Invalid(ContentProblem.MISSING_FILE, "$path not downloaded")
        if (!sha256(data).equals(expected, ignoreCase = true)) return ContentCheck.Invalid(ContentProblem.CHECKSUM_MISMATCH, path)
    }
    val files = ContentFiles { path -> bytes.getValue(path).decodeToString() }
    val deck = runCatching {
        CONTENT_FILES.filter { it.startsWith("i18n/") }.forEach { ContentTranslations.parse(files.read(it)) }
        buildDeckFrom(files)
    }.getOrElse { return ContentCheck.Invalid(ContentProblem.UNREADABLE, it.message ?: it.javaClass.simpleName) }
    GameMode.entries.firstOrNull { deck[it].isNullOrEmpty() }
        ?.let { return ContentCheck.Invalid(ContentProblem.EMPTY_MODE, it.name) }
    return ContentCheck.Valid(deck)
}

/** How many questions the pool gained (negative when a correction removed some). */
fun poolGrowth(before: Map<GameMode, List<Card>>, after: Map<GameMode, List<Card>>): Int =
    after.values.sumOf { it.size } - before.values.sumOf { it.size }

fun sha256(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

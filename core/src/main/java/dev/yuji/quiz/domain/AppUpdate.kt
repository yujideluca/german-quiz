package dev.yuji.quiz.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What `latest.json` in the public releases repo says about the newest published APK. */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
)

private val manifestJson = Json { ignoreUnknownKeys = true }

fun parseUpdateManifest(text: String): UpdateManifest? =
    runCatching { manifestJson.decodeFromString<UpdateManifest>(text) }.getOrNull()

fun UpdateManifest.isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode

/** Minimum time between two automatic update checks, so quickly switching apps does not hammer the feed. */
const val UPDATE_CHECK_MIN_GAP_MILLIS = 30_000L

/** True when the app was opened and the last check is old enough (or there was none yet). */
fun shouldCheckForUpdate(nowMillis: Long, lastCheckMillis: Long?, minGapMillis: Long = UPDATE_CHECK_MIN_GAP_MILLIS): Boolean =
    // A clock set back (now before the last check) must not block checks until it catches up.
    lastCheckMillis == null || nowMillis < lastCheckMillis || nowMillis - lastCheckMillis >= minGapMillis

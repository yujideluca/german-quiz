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

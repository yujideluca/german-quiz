package dev.yuji.quiz.data

import dev.yuji.quiz.domain.UpdateManifest
import dev.yuji.quiz.domain.parseUpdateManifest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Reads the update feed (a public GitHub repo holding only `latest.json` and the newest APK).
 * Files come through the GitHub contents API, which is cached for at most 60 s; raw.githubusercontent.com
 * caches for 5 minutes and ignores query strings, so it would hide a fresh release.
 * [ref] is the feed branch to follow: "main" for the stable app, "bleeding-edge" for the beta app.
 */
/** Why an update step failed. The message stays English (logs, tests); the screen translates by [kind]. */
enum class UpdateFailureKind { MALFORMED_FEED, CHECKSUM_MISMATCH, RATE_LIMIT, SERVER_ERROR }

class UpdateFailure(val kind: UpdateFailureKind, message: String, val httpCode: Int = 0) : IllegalStateException(message)

class UpdateClient(private val baseUrl: String = FEED_URL, private val ref: String = "main") {

    /** Returns the manifest, or throws with a human-readable message. */
    fun fetchManifest(): UpdateManifest {
        val text = open("$baseUrl/latest.json?ref=$ref").use { it.readBytes().decodeToString() }
        return parseUpdateManifest(text) ?: throw UpdateFailure(UpdateFailureKind.MALFORMED_FEED, "The update feed is malformed")
    }

    /** Downloads the APK into [target] and verifies its SHA-256; deletes the file when it does not match. */
    fun downloadApk(manifest: UpdateManifest, target: File) {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        open("$baseUrl/${manifest.apk}?ref=$ref").use { input ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(manifest.sha256, ignoreCase = true)) {
            target.delete()
            throw UpdateFailure(UpdateFailureKind.CHECKSUM_MISMATCH, "Downloaded file is corrupt (checksum mismatch)")
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = TIMEOUT_MILLIS
        readTimeout = TIMEOUT_MILLIS
        useCaches = false
        setRequestProperty("Accept", "application/vnd.github.raw+json")
        when (responseCode) {
            HttpURLConnection.HTTP_OK -> Unit
            HttpURLConnection.HTTP_FORBIDDEN ->
                throw UpdateFailure(UpdateFailureKind.RATE_LIMIT, "GitHub's hourly request limit was reached; try again later")
            else -> throw UpdateFailure(UpdateFailureKind.SERVER_ERROR, "Update server answered $responseCode", responseCode)
        }
        inputStream
    }

    companion object {
        const val FEED_URL = "https://api.github.com/repos/yujideluca/german-quiz-releases/contents"
        private const val TIMEOUT_MILLIS = 15_000
        private const val BUFFER_BYTES = 64 * 1024
    }
}

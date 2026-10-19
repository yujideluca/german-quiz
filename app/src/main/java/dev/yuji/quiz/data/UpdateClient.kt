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
 */
class UpdateClient(private val baseUrl: String = FEED_URL) {

    /** Returns the manifest, or throws with a human-readable message. */
    fun fetchManifest(): UpdateManifest {
        val text = open("$baseUrl/latest.json").use { it.readBytes().decodeToString() }
        return parseUpdateManifest(text) ?: error("The update feed is malformed")
    }

    /** Downloads the APK into [target] and verifies its SHA-256; deletes the file when it does not match. */
    fun downloadApk(manifest: UpdateManifest, target: File) {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        open("$baseUrl/${manifest.apk}").use { input ->
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
            error("Downloaded file is corrupt (checksum mismatch)")
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = TIMEOUT_MILLIS
        readTimeout = TIMEOUT_MILLIS
        useCaches = false
        setRequestProperty("Accept", "application/vnd.github.raw+json")
        when (responseCode) {
            HttpURLConnection.HTTP_OK -> Unit
            HttpURLConnection.HTTP_FORBIDDEN -> error("GitHub's hourly request limit was reached; try again later")
            else -> error("Update server answered $responseCode")
        }
        inputStream
    }

    companion object {
        const val FEED_URL = "https://api.github.com/repos/yujideluca/german-quiz-releases/contents"
        private const val TIMEOUT_MILLIS = 15_000
        private const val BUFFER_BYTES = 64 * 1024
    }
}

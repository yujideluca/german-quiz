package dev.yuji.quiz.data

// REQ UP-1, QP-1

import com.sun.net.httpserver.HttpServer
import dev.yuji.quiz.domain.UpdateManifest
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class UpdateClientTest {
    private val apkBytes = ByteArray(100_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apkBytes).joinToString("") { "%02x".format(it) }
    private lateinit var server: HttpServer
    private var forbidden = false
    private var acceptHeader: String? = null
    private val queries = mutableListOf<String?>()
    private lateinit var client: UpdateClient

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            acceptHeader = ex.requestHeaders.getFirst("Accept")
            queries += ex.requestURI.query
            val body = when {
                forbidden -> null
                ex.requestURI.path.endsWith("latest.json") ->
                    """{"versionCode":8,"versionName":"1.7","apk":"german-quiz.apk","sha256":"$sha"}""".toByteArray()
                else -> apkBytes
            }
            if (body == null) ex.sendResponseHeaders(403, -1) else {
                ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
            }
            ex.close()
        }
        server.start()
        client = UpdateClient("http://127.0.0.1:${server.address.port}")
    }

    @After fun stop() = server.stop(0)

    @Test fun `fetches the manifest and asks for the raw representation`() {
        val m = client.fetchManifest()
        assertEquals(8, m.versionCode)
        assertEquals("application/vnd.github.raw+json", acceptHeader)
    }

    @Test fun `reads the question pool from the content folder of its own feed branch`() {
        val paths = mutableListOf<String>()
        server.createContext("/content/") { ex ->
            paths += ex.requestURI.path + "?" + ex.requestURI.query
            val body = "x".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }; ex.close()
        }
        val beta = UpdateClient("http://127.0.0.1:${server.address.port}", ref = "bleeding-edge")
        assertEquals("x", beta.fetchContentManifestText())
        assertEquals(1, beta.fetchContentFile("vocab/nouns.json").size)
        assertEquals(listOf("/content/content.json?ref=bleeding-edge", "/content/vocab/nouns.json?ref=bleeding-edge"), paths)
    }

    @Test fun `follows the configured feed branch for the manifest and the apk`() {
        val beta = UpdateClient("http://127.0.0.1:${server.address.port}", ref = "bleeding-edge")
        val target = File.createTempFile("update", ".apk")
        beta.downloadApk(beta.fetchManifest(), target)
        target.delete()
        assertEquals(listOf("ref=bleeding-edge", "ref=bleeding-edge"), queries)
    }

    @Test fun `the default channel is the stable branch`() {
        client.fetchManifest()
        assertEquals(listOf("ref=main"), queries)
    }

    @Test fun `downloads the apk and keeps it when the checksum matches`() {
        val target = File.createTempFile("update", ".apk")
        client.downloadApk(client.fetchManifest(), target)
        assertTrue(target.readBytes().contentEquals(apkBytes))
        target.delete()
    }

    @Test fun `a checksum mismatch deletes the download and fails`() {
        val target = File.createTempFile("update", ".apk")
        val wrong = UpdateManifest(8, "1.7", "german-quiz.apk", "00".repeat(32))
        try { client.downloadApk(wrong, target); fail("expected a failure") }
        catch (e: IllegalStateException) { assertTrue("checksum" in e.message!!) }
        assertFalse(target.exists())
    }

    @Test fun `a 403 is explained as the request limit`() {
        forbidden = true
        try { client.fetchManifest(); fail("expected a failure") }
        catch (e: IllegalStateException) { assertTrue("limit" in e.message!!) }
    }
}

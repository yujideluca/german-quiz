package dev.yuji.quiz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val json = """{"versionCode":3,"versionName":"1.2","apk":"german-quiz.apk","sha256":"ab12","extra":true}"""

    @Test fun `parses the manifest and ignores unknown keys`() {
        assertEquals(UpdateManifest(3, "1.2", "german-quiz.apk", "ab12"), parseUpdateManifest(json))
    }

    @Test fun `malformed manifest gives null`() {
        assertNull(parseUpdateManifest("<html>404</html>"))
        assertNull(parseUpdateManifest("""{"versionCode":3}"""))
    }

    @Test fun `only a higher version code counts as newer`() {
        val m = parseUpdateManifest(json)!!
        assertTrue(m.isNewerThan(2))
        assertFalse(m.isNewerThan(3))
        assertFalse(m.isNewerThan(4))
    }

    @Test fun `the first open always checks`() {
        assertTrue(shouldCheckForUpdate(nowMillis = 1_000, lastCheckMillis = null))
    }

    @Test fun `reopening within the minimum gap reuses the last result`() {
        assertFalse(shouldCheckForUpdate(nowMillis = 100_000, lastCheckMillis = 100_000 - UPDATE_CHECK_MIN_GAP_MILLIS + 1))
    }

    @Test fun `reopening after the minimum gap checks again`() {
        assertTrue(shouldCheckForUpdate(nowMillis = 100_000, lastCheckMillis = 100_000 - UPDATE_CHECK_MIN_GAP_MILLIS))
    }
}

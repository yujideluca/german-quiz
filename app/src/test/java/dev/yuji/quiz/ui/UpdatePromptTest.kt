package dev.yuji.quiz.ui

// REQ UP-1

import dev.yuji.quiz.domain.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatePromptTest {
    private val manifest = UpdateManifest(9, "1.8", "german-quiz.apk", "ab")

    @Test fun `a newer version opens the offer on the home screen`() {
        assertEquals(UpdatePrompt.Offer(manifest), updatePromptFor(UpdateState.Available(manifest), dismissed = false, onHomeScreen = true))
    }

    @Test fun `later hides the popup until the app is opened again`() {
        assertNull(updatePromptFor(UpdateState.Available(manifest), dismissed = true, onHomeScreen = true))
    }

    @Test fun `a study session is never interrupted`() {
        assertNull(updatePromptFor(UpdateState.Available(manifest), dismissed = false, onHomeScreen = false))
    }

    @Test fun `download progress failure and the permission step are shown`() {
        assertEquals(UpdatePrompt.Downloading(manifest), updatePromptFor(UpdateState.Downloading(manifest), false, true))
        assertEquals(UpdatePrompt.NeedsPermission(manifest), updatePromptFor(UpdateState.NeedsPermission(manifest), false, true))
        assertEquals(UpdatePrompt.DownloadFailed(manifest, "boom"), updatePromptFor(UpdateState.DownloadFailed(manifest, "boom"), false, true))
    }

    @Test fun `being up to date, checking, or a failed check show nothing`() {
        listOf(UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate, UpdateState.Failed("offline")).forEach {
            assertNull(updatePromptFor(it, dismissed = false, onHomeScreen = true))
        }
    }
}

class UpdateReducerTest {
    private val manifest = UpdateManifest(9, "1.8", "german-quiz.apk", "ab")
    private val offline = Result.failure<UpdateManifest>(java.io.IOException("offline"))

    @Test fun `a known update stays visible while the feed is asked again`() {
        assertEquals(UpdateState.Available(manifest), stateWhileChecking(UpdateState.Available(manifest)))
        assertEquals(UpdateState.Checking, stateWhileChecking(UpdateState.UpToDate))
    }

    @Test fun `a newer version is offered and the same version is up to date`() {
        assertEquals(UpdateState.Available(manifest), stateAfterCheck(UpdateState.Checking, Result.success(manifest), installedVersionCode = 8))
        assertEquals(UpdateState.UpToDate, stateAfterCheck(UpdateState.Checking, Result.success(manifest), installedVersionCode = 9))
    }

    @Test fun `a failed check does not hide an update that is already known`() {
        assertEquals(UpdateState.Available(manifest), stateAfterCheck(UpdateState.Available(manifest), offline, installedVersionCode = 8))
    }

    @Test fun `a failed first check reports the problem for Settings`() {
        assertEquals(UpdateState.Failed("offline"), stateAfterCheck(UpdateState.Checking, offline, installedVersionCode = 8))
    }

    @Test fun `an update found earlier disappears once the app is up to date`() {
        assertEquals(UpdateState.UpToDate, stateAfterCheck(UpdateState.Available(manifest), Result.success(manifest), installedVersionCode = 9))
    }
}

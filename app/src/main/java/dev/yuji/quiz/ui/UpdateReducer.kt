package dev.yuji.quiz.ui

import dev.yuji.quiz.domain.UpdateManifest
import dev.yuji.quiz.domain.isNewerThan

/** An update the user was already told about; it stays on screen while the feed is asked again. */
private fun UpdateState.isKnownUpdate() =
    this is UpdateState.Available || this is UpdateState.NeedsPermission || this is UpdateState.DownloadFailed

/** State to show while a new check runs: a known update stays visible instead of flickering away. */
fun stateWhileChecking(previous: UpdateState): UpdateState =
    if (previous.isKnownUpdate()) previous else UpdateState.Checking

/** State after a check. A failed check (offline, rate limit) never hides an update that is already known. */
fun stateAfterCheck(previous: UpdateState, result: Result<UpdateManifest>, installedVersionCode: Int): UpdateState =
    result.fold(
        onSuccess = { if (it.isNewerThan(installedVersionCode)) UpdateState.Available(it) else UpdateState.UpToDate },
        onFailure = { if (previous.isKnownUpdate()) previous else UpdateState.Failed(it.message ?: "Could not check for updates") },
    )

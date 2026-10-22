# ADR-0004: Distribute through a public feed repo and an in-app updater

- **Status:** accepted (reconstructed 2026-10-22)
- **Date:** 2026-10-19
- **Type (ISO/IEC/IEEE 14764):** perfective (feed), adaptive (signing key, contents API)
- **Decided by:** Giovanni and Claude Code

## Context

The app is not on Google Play. The source repo `yujideluca/german-quiz` is private, so the phone cannot download
builds from it. Play Protect blocked debug-signed, debuggable APKs.

## Decision

A separate public repo `yujideluca/german-quiz-releases` holds only `latest.json` and the newest signed release
APK. The app reads them through the GitHub contents API, offers a higher `versionCode`, checks the SHA-256 and opens
Android's installer. All builds are signed with one own key kept outside the repo (`~/.android/german-quiz-release.jks`).

## Alternatives considered

- **raw.githubusercontent.com** — caches 5 minutes and ignores query strings; a fresh release stayed invisible and a
  stale manifest could meet a new APK. Replaced by the contents API (60 s cache, 60 requests/h per IP).
- **Google Play** — review overhead and a developer account for a personal app.
- **Debug key** — Play Protect blocked it.

## Consequences

Losing the signing key means no in-place update (users would have to reinstall and restore a backup), so it must be
backed up off the laptop. Every new APK may need a Play Protect tap on the phone.

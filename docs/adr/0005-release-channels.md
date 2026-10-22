# ADR-0005: Two release channels: beta first, stable only on Giovanni's word

- **Status:** accepted (reconstructed 2026-10-22); amended by [ADR-0011](0011-promote-after-updated-tests.md) (when a version may be promoted)
- **Date:** 2026-10-19
- **Type (ISO/IEC/IEEE 14764):** preventive
- **Decided by:** Giovanni

## Context

A broken build must never reach the app that holds the real study progress.

## Decision

Two build flavors that install side by side with separate data: `stable` (`dev.yuji.quiz`, feed branch `main`) and
`beta` (`dev.yuji.quiz.beta`, feed branch `bleeding-edge`). `tools/publish_apk.sh beta` publishes to the beta channel;
`tools/publish_apk.sh promote` publishes the stable flavor only if bleeding-edge holds the same versionCode and
commit and the tree is clean, and only when Giovanni says the beta is good.

## Alternatives considered

- **A runtime switch in one app** — test data would mix with real progress.
- **One channel** — no place to try a build on the phone before it reaches the real app.

## Consequences

Promotion rebuilds from the same commit instead of copying the tested bytes. The check is in the script, not in
anyone's memory.

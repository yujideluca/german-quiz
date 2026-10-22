# ADR-0009: Pure Kotlin core module, CI and fitness functions

- **Status:** accepted
- **Date:** 2026-10-22
- **Type (ISO/IEC/IEEE 14764):** preventive
- **Decided by:** Giovanni, from the maintenance and evolution review of 2026-10-22

## Context

"`domain/` and `content/` stay free of Android imports" was a sentence in `CLAUDE.md`; nothing failed if it was
broken. The quality gate ran only on the laptop and only when a release was published, so a push could carry
broken tests for days. Nothing measured whether the code was getting bigger or slower to check.

## Decision

1. **Module boundary enforced by the compiler.** `domain/`, `content/` (with the vocabulary parsers) and `debug/`
   live in a Gradle module `:core` with the Kotlin/JVM plugin only; `:app` depends on it. Android is not on
   `:core`'s classpath, so `import android.*` there does not compile (checked on 2026-10-22 with a planted import).
   Package names did not change.
2. **CI.** `.github/workflows/quality-gate.yml` runs `tools/quality_gate.sh` on every push and pull request, and with
   the mutation check every Monday. The web repo runs its tests, including `tests/architecture.test.js`
   (who may import whom; only `app.js` and `store.js` touch the browser), on every push.
3. **Fitness functions** (`tools/fitness_check.py`, run by the gate and by `publish_apk.sh`): `core/build.gradle.kts`
   has no Android; no Kotlin file over 400 lines; release APK under 3 MB; the gate itself under 20 minutes.
   Limits are raised only on purpose, with a decision-log row.

## Alternatives considered

- **Konsist or ArchUnit rule tests** — work inside one module, but a test can be skipped or deleted; the compiler
  boundary cannot. Can be added later for rules inside `:app` (for example `ui` must not reach `data`).
- **dependency-cruiser for the web** — the web has no dependencies at all; a 40-line `node:test` file keeps it so.

## Consequences

Tests of pure logic moved to `core/src/test` (97 then, 101 after two new tests); the conformance vectors moved to
`core/src/test/resources/conformance/`. Coverage is read from both modules' JaCoCo reports. CI on a private repo
uses GitHub Actions minutes (2 000 per month free); the weekly mutation run is the largest user.

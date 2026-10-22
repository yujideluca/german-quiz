# ADR-0006: A web twin kept in step by shared vectors and a sync check

- **Status:** accepted (reconstructed 2026-10-22)
- **Date:** 2026-10-19
- **Type (ISO/IEC/IEEE 14764):** perfective
- **Decided by:** Giovanni

## Context

The quiz should also run on an iPhone and in a browser, without a store and without a second backend.

## Decision

A port as a plain-ES-module PWA (`yujideluca/german-quiz-web`, GitHub Pages), no build step and no dependencies.
It follows the **user** version: every promote also updates the web. The two stay equal through checks, not care:
the same conformance vectors (`core/src/test/resources/conformance/*.json`, copied to the web's `tests/conformance/`),
the same vocabulary and translation files, UI strings generated from the app's resources, and
`tools/check_web_sync.sh` at the end of `promote`. A port of an app version still in beta waits on a web branch
`web-<versionName>` and is merged into `main` (which Pages serves) at promote.

## Alternatives considered

Not recorded at the time.

## Consequences

Every scheduler or content change is done twice. The shared vectors and the mutation check (which also plants
faults in the web code) catch drift; the CI of both repos runs them on every push.

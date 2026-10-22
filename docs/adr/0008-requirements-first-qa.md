# ADR-0008: Requirements first, and a quality gate in front of every release

- **Status:** accepted (reconstructed 2026-10-22)
- **Date:** 2026-10-19
- **Type (ISO/IEC/IEEE 14764):** preventive
- **Decided by:** Giovanni

## Context

See ADR-0007: tests that restate the code cannot protect a rule nobody wrote down.

## Decision

A behaviour Giovanni states becomes a row in `docs/REQUIREMENTS.md` (with an ID), then a test tagged `REQ <ID>`,
then code. `tools/quality_gate.sh` is the exit criterion: unit, property, conformance and integration tests,
coverage floors, app/web vector equality, traceability, lint and fitness functions; `promote` adds the mutation check.
`publish_apk.sh` refuses to publish if the gate fails. Details: `docs/TESTING.md`.

## Alternatives considered

- **Tests only, no requirement IDs** — the situation that produced the spacing defect.

## Consequences

Each rule costs a requirement row and a tagged test before code. The gate is one script, run the same way on the
laptop and in CI (ADR-0009).

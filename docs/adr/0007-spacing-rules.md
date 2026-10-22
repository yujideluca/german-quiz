# ADR-0007: The spacing rules are requirements, pinned by tests

- **Status:** accepted (reconstructed 2026-10-22)
- **Date:** 2026-10-19
- **Type (ISO/IEC/IEEE 14764):** corrective
- **Decided by:** Giovanni

## Context

The scheduler matched Anki's defaults, so its tests passed, yet Giovanni saw missed cards come back as the first
question when re-opening a quiz and new cards return within minutes. The rule had never been written down, and the
tests restated the implementation.

## Decision

Three rules are the app's first principle (requirements SP-1..SP-6): a new card answered correctly returns after
3 days, never earlier; a wrong answer returns after 10 minutes, never earlier, not even after leaving and re-opening
the quiz; every further correct answer pushes the next showing later. They are checked at the level the player sees
(`StudyController.startSession`), not only in the scheduler.

## Alternatives considered

- **Anki defaults (1 min, 10 min, 1 day)** — what the app had; rejected by the person using it.

## Consequences

`SpacingRulesTest`, `PeriodizationSimulationTest`, conformance vectors and planted mutants protect the rules; a
change to them needs Giovanni and a new ADR.

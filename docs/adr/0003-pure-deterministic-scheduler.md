# ADR-0003: Pure, deterministic scheduler with an injected clock

- **Status:** accepted (reconstructed 2026-10-22)
- **Date:** before 2026-10-15
- **Type (ISO/IEC/IEEE 14764):** initial
- **Decided by:** Giovanni and Claude Code

## Context

Spaced repetition decides when a card comes back. Anki adds random "fuzz" to intervals and its scheduler reads
the clock itself; both make a behaviour hard to reproduce in a test or a bug report.

## Decision

`SpacedRepetitionScheduler` is a pure function `(progress, grade, now, config) -> progress` with no randomness.
Time enters only through the `Clock` interface. The grade is inferred from correctness, speed and phase
(`AnswerGrader`), because multiple choice has no self-assessment.

## Alternatives considered

- **Anki's scheduler with fuzz** — spreads review load, but the same answers would not give the same dates.
- **Self-assessed grades (again/hard/good/easy buttons)** — one extra tap per card, and multiple choice already
  measures correctness and speed.

## Consequences

Every rule can be checked with exact numbers (conformance vectors, property tests with an independent oracle).
Cards learned on the same day come due on the same day; accepted.

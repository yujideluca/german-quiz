# ADR-0001: Record architecture decisions as ADRs

- **Status:** accepted
- **Date:** 2026-10-22
- **Type (ISO/IEC/IEEE 14764):** preventive
- **Decided by:** Giovanni, from the maintenance and evolution review of 2026-10-22

## Context

`DESIGN.md` has kept a dated decision log since the start (about 40 rows). Each row says what and why, but not
which alternatives were rejected or what must stay true afterwards, and a later decision that replaces an earlier one
is just another row. In two years nobody would know which of the three home-screen layouts is the current one
without reading them all.

## Decision

Structural decisions get an ADR in `docs/adr/` (format in `0000-template.md`), written in the same commit as the
change. The decision log in `DESIGN.md` stays as the one-line history and links to the ADR. Each ADR carries the
ISO/IEC/IEEE 14764 maintenance type, so the balance between fixing, adapting, adding and preventing is visible.

## Alternatives considered

- **Only the decision log** — cheap, but loses alternatives and supersession, which is exactly what the log lacked.
- **A wiki or Google Doc** — not versioned with the code, so it drifts.

## Consequences

Some extra writing per structural change. `CLAUDE.md` tells Claude Code when an ADR is needed; the monthly
architecture review checks that new structural changes have one.

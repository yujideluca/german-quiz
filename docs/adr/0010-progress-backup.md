# ADR-0010: Progress backup as one JSON file, import replaces

- **Status:** accepted (reconstructed 2026-10-22)
- **Date:** 2026-10-19
- **Type (ISO/IEC/IEEE 14764):** preventive
- **Decided by:** Giovanni and Claude Code

## Context

Moving to a new phone, or reinstalling after a signing-key change, must not lose months of progress.

## Decision

Settings > Backup exports one JSON file (format version, card states, answer history, daily goal, theme) through
Android's document picker, so the user chooses local storage or Google Drive and the app needs no storage
permission. Import **replaces** the current progress after a confirmation; invalid entries are dropped and a file
from a newer format is refused. The codec is in `core` and JVM-tested.

## Alternatives considered

- **Merge on import** — needs conflict rules that are hard to explain; rejected.

## Consequences

A damaged `progress.json` is kept as `progress.json.corrupt` before the next save (found by the QA work of the same
day). The web version has its own backup (a JSON download); no test checks that the two formats are compatible,
so a backup is meant for the platform that wrote it.

# ADR-0002: Kotlin and Jetpack Compose

- **Status:** accepted (reconstructed 2026-10-22)
- **Date:** before 2026-10-15
- **Type (ISO/IEC/IEEE 14764):** initial
- **Decided by:** Giovanni and Claude Code

## Context

A personal Android app for drilling B2 German, built and maintained almost entirely by Claude Code.

## Decision

Kotlin with Jetpack Compose (declarative UI), immutable state, one Activity.

## Alternatives considered

- **Scala, Rust, Python, Elixir** — none is a first-class Android route; tooling, libraries and documentation are thin.

## Consequences

Compose fits an immutable-state design: screens are functions of state. Gradle and the Android Gradle Plugin
must be kept current (adaptive maintenance), which is the main recurring cost.

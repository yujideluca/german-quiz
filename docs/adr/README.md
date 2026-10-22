# Architecture decision records

One short file per decision that shapes the structure of the app or the way it is built and released
(format: [MADR](https://adr.github.io/madr/), trimmed). `DESIGN.md` section 11 keeps the full, dated log of every
design change in one line each; an ADR is written when a decision has alternatives worth remembering or rules
that must stay true. Copy `0000-template.md`, take the next number, and link the ADR from the log row.

An ADR is never rewritten after it is accepted: a new decision gets a new ADR, and the old one's status
becomes "superseded by ADR-XXXX". Rows marked *reconstructed* were written on 2026-10-22 from the decision log
and commit history; their alternatives are the ones recorded then, nothing more.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-record-decisions.md) | Record architecture decisions as ADRs | accepted |
| [0002](0002-kotlin-compose.md) | Kotlin and Jetpack Compose | accepted (reconstructed) |
| [0003](0003-pure-deterministic-scheduler.md) | Pure, deterministic scheduler with an injected clock | accepted (reconstructed) |
| [0004](0004-update-feed.md) | Distribute through a public feed repo and an in-app updater | accepted (reconstructed) |
| [0005](0005-release-channels.md) | Two release channels: beta first, stable only on Giovanni's word | accepted (reconstructed), amended by 0011 |
| [0006](0006-web-twin.md) | A web twin kept in step by shared vectors and a sync check | accepted (reconstructed) |
| [0007](0007-spacing-rules.md) | The spacing rules are requirements, pinned by tests | accepted (reconstructed) |
| [0008](0008-requirements-first-qa.md) | Requirements first, and a quality gate in front of every release | accepted (reconstructed) |
| [0009](0009-pure-core-module.md) | Pure Kotlin `core` module, CI and fitness functions | accepted |
| [0010](0010-progress-backup.md) | Progress backup as one JSON file, import replaces | accepted (reconstructed) |
| [0011](0011-promote-after-updated-tests.md) | Promote once the updated automated tests pass; then Giovanni decides | accepted |
| [0012](0012-downloadable-question-pool.md) | The question pool is a versioned pack the app can download from the update feed | accepted |

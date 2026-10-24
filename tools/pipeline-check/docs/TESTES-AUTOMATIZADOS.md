# Automated bug-finding tests: pipeline-check

Last reviewed: 64020bd 2026-10-24
Created by the skill `gerar-teste-automatizado` (create mode). Update mode starts from the commit above.

## Analysis summary
Rust crate, `std` plus `serde`, `toml`, `clap`. Pure logic (classification, parsing, report) is separated from effects (running the gate, `git`, `gh`), and `gh` sits behind a trait, so most behaviour is testable without a network. Risks, by cost of error: a wrong `ok` that lets a violation merge (PC-3, PC-4), a wrong `falha` that blocks good work (PC-11), a hang in CI (PC-10), silent config errors (PC-8). Strategy: unit tests for the pure pieces, end-to-end tests on fixture projects with the real binary, and defect-oriented tests whose expected values come from `docs/REQUIREMENTS.md` (PC-1..PC-11). Observed: everything below was executed. Inferred: the list of risks. Hypothesis: none needed.

## Test matrix (excerpt; full mapping in docs/TRACEABILITY.md)
| Requirement | Scenario | Expected | Priority | File |
|---|---|---|---|---|
| PC-2 | value 79/80/85/99/100/250 of 100 | ok/aviso/aviso/aviso/falha/falha | high | src/report.rs |
| PC-3 | `import android.*` in `core/` | falha with file:line | high | tests/end_to_end.rs |
| PC-3 | Android only in a build-file comment | ok | medium | tests/bugs.rs |
| PC-4 | gate skipped, required or not | falha / pulado | high | tests/end_to_end.rs |
| PC-7 | gh missing, 403, 404, wrong job | pulado / pulado / falha / falha | high | src/github.rs |
| PC-8 | missing file, typo key (also top level), non-positive limit | exit 2 / error | high | src/config.rs, tests/bugs.rs |
| PC-9 | `import static android...`, `android.util.Log.d()` without import | falha | high | tests/bugs.rs |
| PC-9 | android in comments, block comments, longer names | ok | medium | tests/bugs.rs |
| PC-10 | symlink pointing back up; symlink to a file | terminates; not listed | high | tests/bugs.rs |
| PC-11 | cancelled latest run (before success / before failure / alone) | ok / falha / aviso | high | tests/bugs.rs |
| PC-1 | measure-only runs no gate, no GitHub, no DORA | only `arquitetura` | medium | tests/bugs.rs |

## Files
`src/*.rs` unit tests (19); `tests/end_to_end.rs` (11, fixture projects and the real binary); `tests/bugs.rs` (12, defect-finding); `mutants.py` (22 hand-made mutants).

## How to run
```
cargo test --manifest-path tools/pipeline-check/Cargo.toml
python3 -I tools/pipeline-check/mutants.py     # demands that every planted fault makes the tests fail
```
The gate runs the first command (step 7/7).

## Results (observed, 2026-10-24)
42 tests run, 42 passed, 0 failed, 0 ignored. Before the fixes the new `tests/bugs.rs` had 6 failures out of 8 (the other 2 passed and documented already-correct behaviour). Mutation: 21 mutants killed, 0 survived, 1 invalid (does not compile, dropped). Two real gaps were found by survivors (build-file comment, top-level unknown key, measure-only) and closed with tests. Repeated runs are stable.

## Coverage
Not measured: `cargo-llvm-cov` and `cargo-mutants` are not installed. The hand-made mutants stand in for them; branch coverage is unknown.

## Defects found (all fixed in 64020bd)
1. `import static android...` (Java) was not detected. Impact: an Android dependency slips into `:core` unnoticed. Reproduce: `tests/bugs.rs::a_java_static_import_of_android_is_a_violation`.
2. A fully qualified use without import (`android.util.Log.d`) was not detected. Same impact.
3. `walk` followed symbolic links: a link pointing up looped forever and a link to a file was listed as a file. Impact: a hung CI job.
4. A cancelled latest CI run made "CI green" report `falha`. Impact: false red; the success rate counted cancelled runs as failures.
5. (Found by mutation) the build-file check, the top-level typo rejection and `--measure-only` had no test that would notice them broken.

## Limitations and residual risks
String literals containing `android.` in `core/` would be flagged (accepted, rare in a pure module). `gh` real output formats are only tested with fakes; the real command was run once (403 on a private free-plan repo). Time zones: dates are UTC. No concurrency or performance tests (a single short process).

## Recommendations
1. Install `cargo-llvm-cov` and `cargo-mutants` to replace the hand-made mutants and get branch coverage.
2. Add a contract test that runs the real `gh` against a public repository, in local mode only.
3. Decide whether a string literal naming an Android package in `core/` should be allowed.

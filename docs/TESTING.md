# Test strategy: Software Quality Assurance and Verification & Validation

**Verification** asks "did we build the thing right?" (code against its specification).
**Validation** asks "did we build the right thing?" (the product against what Giovanni actually needs).
**Quality assurance** is the process that makes both routine: requirements first, tests traceable to
them, measurable exit criteria, and a gate that blocks a release that does not meet them.

The 2026-10-19 spacing incident showed what happens without it: the scheduler matched Anki's defaults
(verification looked fine) but not what the player sees when leaving and re-opening a quiz (validation
failed), because the rule was never written down and tests restated the implementation.

## 1. Process (QA)

1. A rule from Giovanni becomes a row in `REQUIREMENTS.md` **before** code changes.
2. Each requirement gets at least one test tagged `REQ <ID>` (Kotlin and JavaScript).
3. `tools/quality_gate.sh` is the exit criterion; `publish_apk.sh` refuses to publish if it fails, and GitHub Actions
   runs the same script on every push (`.github/workflows/quality-gate.yml`; with the mutation check every Monday).
4. Defects found later get a regression test under the requirement they violated, plus a decision-log row.

## 2. Risk-based focus

| Area | Risk if wrong | Techniques |
|---|---|---|
| Scheduler, grader, queue, session (`domain/`) | Core promise broken, silent wrong learning | specification tests, boundary values, property tests with an independent oracle, mutation checks, app/web conformance vectors |
| Persistence and backup | Losing months of progress | fault injection (damaged file), round-trips, restart simulation |
| Study flow as the player sees it (`StudyController`) | Rules hold in isolation but not in use | integration tests with a fake clock; leave/return scenarios |
| Content (1 300+ items) | Wrong answers taught | data-integrity tests over every item |
| Languages, update | Wrong text, bad install | table tests (parity), mocked HTTP |
| UI | Unusable screens | instrumented fit test on a device; acceptance run with the real deck |
| Web twin | Drift from the user version | shared conformance vectors, `check_web_sync.sh` |

## 3. Levels and techniques

* **Unit** (JVM, no device): specification-based tests, equivalence classes and **boundary values**
  (for example 2 499 / 2 500 ms, 9 999 / 10 000 ms, 3 days minus 1 ms).
* **Integration** (`StudyController` with fake clock and in-memory stores): complete flows over days.
* **Property-based** (`SchedulingPropertyTest`): thousands of seeded random study histories, each step
  checked against an independent oracle written from the requirements, not from the code.
* **Conformance** (`src/test/resources/conformance/scheduler.json`, hand-computed from the requirements):
  the Kotlin and the JavaScript scheduler must reproduce every row; the gate checks both copies are identical.
* **Progress bar** (DG-2..DG-5): `DailyProgressPropertyTest` (2 000 random histories of answers, repeated taps,
  card management, goal changes, midnight, restarts and backups against a plain tally), `conformance/daily_bar.json`
  (goal snapping, fraction, percent and "reached" vectors; Kotlin and JavaScript both run it), boundary tests
  (empty, 90 %, exactly full, midnight to the millisecond) and `androidTest/.../DailyBarAcceptanceTest`
  (real view model, real vocabulary, in-memory store, fake clock).
* **Mutation check** (`tools/mutation_check.py`): plants known faults (wrong constants, flipped
  comparisons) in the scheduler, grader, pipeline and session; every mutant must make a test fail.
  This validates the tests themselves.
* **Acceptance on a device** (`androidTest/.../StudyFlowAcceptanceTest`): the real deck from the app's
  assets through every mode with in-memory stores (the user's data is never touched), plus the screen-fit test.
* **Static**: the compiler plus Android lint (`lintStableDebug`) are part of the gate.

## 4. Exit criteria (the gate, `tools/quality_gate.sh`)

1. All JVM tests pass (`:core` and `:app`) and `npm test` passes (web, including its architecture rules).
2. Line coverage of the logic packages (`domain/`, `content/`, `StudyController`) >= 90 % and
   no logic file below 80 % (`tools/check_coverage.py`, from JaCoCo).
3. Every requirement has a tagged test; no tag names an unknown requirement (`tools/check_traceability.py`).
4. The conformance vectors in the app (`core/src/test/resources/conformance/`) and in the web clone are identical.
5. Fitness functions hold (`tools/fitness_check.py`): `:core` has no Android, no Kotlin file over 400 lines, the gate
   finishes within 20 minutes; `publish_apk.sh` also checks the release APK stays under 3 MB (ADR-0009).
6. Before a **promote**: the mutation check kills every mutant (`--mutation`).
7. Not automatable here and therefore stated openly in each report: instrumented tests need an unlocked
   phone; UI looks (taste), real-world update installs.

## 5. Known gaps

* Compose screens and `StudyViewModel`/`MainActivity` are only exercised on a device (UI-1, A-level);
  they hold little logic because decisions live in `domain/` and are covered there.
* The `ON_RESUME` call in `MainActivity` that makes the home screen refresh (DG-4) is checked by reading
  the code and by testing `StudyViewModel.refreshHome()` on the device; no automated test moves a real
  activity through the background overnight.
* Coverage percentages are a floor, not a proof: requirement coverage (section 4.3) and mutation score are the real measures.

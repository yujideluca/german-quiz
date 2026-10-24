# Automated bug-finding tests: German Quiz (app)

Last reviewed: 333ef5f 2026-10-24
Created by the skill `gerar-teste-automatizado` (create mode) during the first maintenance review. Update mode starts from the commit above.

## Analysis summary
The `:core` module (1 110 lines of pure Kotlin) holds the rules that matter: spaced repetition, grading, queue, session, daily goal and calendar, backup import, content-pack validation, update checks. The project already has a strong suite (requirements with `REQ` tags, property tests with an independent oracle, shared conformance vectors with the web twin, a 30-fault mutation check, 96.7 % line coverage of the logic packages). This review therefore did not add volume; it hunted for what that suite cannot see: places where the oracle is the code itself, and numeric and boundary edges outside the usual range. Risks by cost of error: wrong results or lost progress (backup import, scheduling), a bad downloaded pool (QP-2), the update path (UP-1). Observed: all tests below ran. Inferred: the risk ranking. Hypotheses are marked.

## Test matrix (new file `core/src/test/java/dev/yuji/quiz/domain/BugHuntTest.kt`)
| Requirement | Scenario | Expected (by hand) | Priority |
|---|---|---|---|
| DG-1 | stored goal `Int.MAX_VALUE`, 2 000 000 000 | 100 | high |
| DG-1 | stored goal `Int.MIN_VALUE` | 10 | high |
| DG-1 | goals -5..1000 around each step, halves | nearest step, halves up | medium |
| PE-3 | backup with an overflowing goal | imports as 100 | high |
| PE-3 | key differs from card id; history counts 0 and -4 | dropped | medium |
| DG-2 | `DailyProgress(0, 0).fraction` | defined (finding F2, @Ignore) | low |
| CA-1 | empty today / empty yesterday | streak kept / ended | medium |
| CA-1 | zero-count day and unordered input | longest run 4 | medium |
| CA-1 | October 2026 | 3 blanks (Thursday start), 31 days | low |
| QP-2 | other schema; file missing from manifest or download; wrong checksum | refused with the matching problem | high |
| QP-2 | equal, older, other-schema and newer packs | only strictly newer, supported schema wins | high |
| UP-1 | update check at gap-1 ms, at exactly the gap | no / yes | medium |
| UP-1 | clock set back (finding F3, @Ignore) | check runs | low |
| MG-1 | search "STUHLE" finds "Stühle" | found | medium |
| MG-1 | search "strasse" finds "Straße" (finding F1, @Ignore) | found | low |

## How to run
```
source ~/dev/env.sh
./gradlew :core:test --tests '*BugHuntTest*'     # only this file
tools/quality_gate.sh                              # everything; --mutation for the planted-fault check
```

## Results (observed, 2026-10-24)
`:core:test` after the fix: all test classes pass; `BugHuntTest` has 17 tests, 14 run and pass, 3 skipped (the @Ignore findings). Before the fix 3 of them failed (below) and 1 failed because of a wrong expectation of mine (longest streak is 4, not 3), corrected. The full quality gate passed. The existing 30-fault mutation check was not rerun in this review (slow, run weekly by CI and by `promote`).

## Coverage
Not re-measured here: the gate's JaCoCo floor (logic packages 96.7 % lines at the last run) stays the measure. Branch coverage is not reported by the gate.

## Defects found
1. **Fixed (corrective).** `clampDailyGoal` overflowed for very large or very small stored values: `Int.MAX_VALUE` became 10 instead of 100, `Int.MIN_VALUE` became 100 instead of 10. Reached through a restored backup with an absurd `dailyGoal`. Impact: a wrong goal after import (small). The existing test compared the function with itself (`clampDailyGoal(100_000) == decode(...)`), so the oracle could not notice. Fix: clamp before snapping. Reproduce: `BugHuntTest."a huge stored goal snaps to the maximum"`.
2. **Finding F1, not fixed (hypothesis).** Search does not fold the sharp s: "strasse" does not find "Straße". The doc says "case- and umlaut-insensitive"; whether ß counts is Giovanni's call.
3. **Finding F2, not fixed.** `DailyProgress(0, 0).fraction` is NaN, against DG-2 "never undefined". Unreachable today because the goal is clamped to at least 10.
4. **Finding F3, not fixed (hypothesis).** When the phone clock is set back, `shouldCheckForUpdate` stays false until the clock catches up, because the elapsed time is negative.

## Limitations and residual risks
Android UI code, the downloader and the installer are outside `:core` and not covered by these tests (device acceptance tests exist). Only hand-chosen boundary cases were added; no new property tests. The suite relies on the weekly mutation run to prove it detects faults.

## Recommendations
1. Decide F1 and F3, then un-ignore the two tests (and fix); F2 can be fixed together with them.
2. Replace oracles that call the function under test (search the tests for `assertEquals(f(x), ...f(x)...)`) with hand-computed values, starting with `ProgressBackupTest`.
3. Add branch coverage to the JaCoCo report.

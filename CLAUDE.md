# German Quiz: working notes for Claude Code

## Principles (each one is checked by something; see `docs/MAINTENANCE.md`)

1. **The spacing rules come first** (ADR-0007): checked by `SpacingRulesTest`, `PeriodizationSimulationTest`, vectors, mutants.
2. **Logic is framework-free**: scheduler, grader, queue, session and content live in `:core`, which has no Android;
   an `import android.*` there does not compile (ADR-0009).
3. **Requirement, then test, then code** (ADR-0008): checked by the traceability gate.
4. **Nothing reaches the player without the beta app first** (ADR-0005), and a version is eligible for promote once it
   survives the automated tests updated for what it changes (ADR-0011): gate with mutation + `tools/check_tests_updated.sh`,
   both run by `publish_apk.sh promote`. Then promoting is Giovanni's call; no minimum time in beta.
5. **The web twin behaves like the user version** (ADR-0006): shared vectors, `check_web_sync.sh`, both CIs.
6. **Decisions are written where the code is**: decision-log row with its Type in `DESIGN.md`; an ADR in `docs/adr/`
   for structural ones; the C4 diagrams in `DESIGN.md` section 3 change in the same commit.

## Working rules

- Environment: `source ~/dev/env.sh` (JDK 21, Gradle, Android SDK). Build and test with
  `./gradlew :core:test testStableDebugUnitTest assembleStableDebug` (flavors: `stable` and `beta`), or the whole gate
  with `tools/quality_gate.sh`. GitHub Actions runs that gate on every push: after pushing, the run must be green.
- After coding anything, do a first-line debug pass before reporting done: tests, install on the
  phone, exercise the changed flow, check `adb logcat` for crashes.
- **Keep `DESIGN.md` current.** In the same commit as any change to architecture, modes,
  scheduler/grader, persistence or UI flow: add a row to the decision log (newest first), fix any
  section that is now wrong, and bump "Last updated". Give the row its Type (Corrective, Adaptive, Perfective,
  Preventive). A structural change (module, dependency direction, release flow, storage format, a rule with real
  alternatives) also gets an ADR (`docs/adr/0000-template.md`), linked from the row. When asked to commit or at the end of a
  work session, check that DESIGN.md still matches the code.
- `domain/`, `content/` and `debug/` live in the `:core` module (pure Kotlin/JVM); their tests are in `core/src/test`.
  Never add an Android plugin or dependency to `core/build.gradle.kts` (pipeline-check fails). Fitness limits in
  `pipeline-check.toml` are raised only on purpose, with a decision-log row saying why.
- **Releases:** run `tools/publish_apk.sh beta` after committing: it goes to the feed's `bleeding-edge`
  branch (the beta app). Run `tools/publish_apk.sh promote` (feed branch `main`, what users get) **only when
  Giovanni says the beta is good**; it refuses unless bleeding-edge holds that same commit and the tests were updated
  since the last stable release (ADR-0011). When a beta passes, tell Giovanni it is eligible and let them decide.
- **New questions need no app release** (ADR-0012): edit `tools/data/*.tsv`, run `tools/build_vocab.py` (raises the pool
  version in `assets/content.json`), raise the pool-size floors in `ContentTest` if useful, commit, push, then
  `tools/publish_content.sh beta`. `tools/publish_content.sh promote` (main + web version) only when Giovanni says so, like an
  APK. A new mode or a new field in the files is an app change: raise `CONTENT_SCHEMA` and `SCHEMA` in `tools/content_manifest.py`.
- **The web version follows the user version.** Every time the user version is updated (a `promote`), update the web version too:
  https://yujideluca.github.io/german-quiz-web/ (repo `yujideluca/german-quiz-web`, clone `~/dev/QuizWeb`, plain ES modules, `npm test`).
  While the app version is still in beta, its web port waits on the web branch `web-<versionName>` (CI picks it up);
  at promote, merge it into the web `main`. Port the change (copy the `vocab/*.json` and `i18n/*.json` files, port code and tests (UI text: regenerate `src/strings.js` from the app's string resources), bump `VERSION` in its `sw.js`), push, and confirm the live
  site serves it. `tools/check_web_sync.sh` (run at the end of `promote`) fails with exit 2 while the web version is behind. Giovanni
  asked for this once; do not wait to be asked again. Say in the report that the web link was updated (and give it).
- **Spacing rules are the app's first principle; never change them silently, and never trust the scheduler's defaults.**
  (1) A new card answered correctly returns after 3 days, never earlier. (2) A wrong answer returns after 10 minutes, never
  earlier: not in the same session, not as the first card when the player leaves the quiz and comes back. (3) Every further
  correct answer pushes the next showing later than the last one. These are pinned by `SpacingRulesTest` and
  `PeriodizationSimulationTest`; when you touch the scheduler, queue or session code, check those rules against what the
  **player sees when they leave and re-open a quiz** (`StudyController.startSession`), not only against the scheduler's output.
  A rule that Giovanni states is a requirement, so write it into this list and into a test in the same change.
- **Quality assurance:** follow `docs/TESTING.md`. A behaviour Giovanni states is first a row in `docs/REQUIREMENTS.md`
  (with an ID), then a test tagged `// REQ <ID>`, then code. `tools/quality_gate.sh` must pass (it is run by
  `publish_apk.sh`; `promote` adds `--mutation`). When you add a rule to the scheduler, queue or session, add a
  mutant for it to `tools/mutation_check.py` and a vector to `core/src/test/resources/conformance/scheduler.json`
  (copy it to `~/dev/QuizWeb/tests/conformance/`). Report which levels were actually run, and which could not be.
- **Technical debt** (`docs/MAINTENANCE.md`): a limitation or shortcut found while working becomes a GitHub Issue
  labelled `debt` with a priority (`P1`..`P3`) in the same session. About one session in five goes to debt or
  prevention: when the last four sessions were all features, say so and propose the top `debt` issue; Giovanni decides.

# German Quiz: working notes for Claude Code

- Environment: `source ~/dev/env.sh` (JDK 21, Gradle, Android SDK). Build and test with
  `./gradlew testStableDebugUnitTest assembleStableDebug` (flavors: `stable` and `beta`).
- After coding anything, do a first-line debug pass before reporting done: tests, install on the
  phone, exercise the changed flow, check `adb logcat` for crashes.
- **Keep `DESIGN.md` current.** In the same commit as any change to architecture, modes,
  scheduler/grader, persistence or UI flow: add a row to the decision log (newest first), fix any
  section that is now wrong, and bump "Last updated". When asked to commit or at the end of a
  work session, check that DESIGN.md still matches the code.
- `domain/` and `content/` stay free of Android imports so they remain JVM-testable.
- **Releases:** run `tools/publish_apk.sh beta` after committing: it goes to the feed's `bleeding-edge`
  branch (the beta app). Run `tools/publish_apk.sh promote` (feed branch `main`, what users get) **only when
  Giovanni says the beta is good**; it refuses unless bleeding-edge holds that same commit.
- **The web version follows the user version.** Every time the user version is updated (a `promote`), update the web version too:
  https://yujideluca.github.io/german-quiz-web/ (repo `yujideluca/german-quiz-web`, clone `~/dev/QuizWeb`, plain ES modules, `npm test`).
  Port the change (copy the `vocab/*.json` and `i18n/*.json` files, port code and tests (UI text: regenerate `src/strings.js` from the app's string resources), bump `VERSION` in its `sw.js`), push, and confirm the live
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
  mutant for it to `tools/mutation_check.py` and a vector to `app/src/test/resources/conformance/scheduler.json`
  (copy it to `~/dev/QuizWeb/tests/conformance/`). Report which levels were actually run, and which could not be.

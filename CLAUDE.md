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
  Port the change (copy the `vocab/*.json` files, port code and tests, bump `VERSION` in its `sw.js`), push, and confirm the live
  site serves it. `tools/check_web_sync.sh` (run at the end of `promote`) fails with exit 2 while the web version is behind. Giovanni
  asked for this once; do not wait to be asked again. Say in the report that the web link was updated (and give it).

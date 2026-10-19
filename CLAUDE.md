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

# German Quiz: design and development decisions

An offline Android app for drilling basic B2 German vocabulary with Anki-style spaced repetition.
This file explains **how the app is built** and **why** each choice was made. It is a living
document: see [How this file is maintained](#how-this-file-is-maintained).

Last updated: 2026-10-19

---

## 1. What the app is

Four study modes over a fixed, curated B2 pool (nothing beyond the words every B2 speaker should know):

| Mode | Question | Pool |
|---|---|---|
| 2026-10-19 | In-app update from GitHub: on start and from Settings the app reads `latest.json` in the public repo `yujideluca/german-quiz-releases` (only the newest APK lives there; `tools/publish_apk.sh` builds, writes versionCode/sha256 and pushes). A higher versionCode shows a dialog; Update downloads, checks the SHA-256 and opens Android's installer. Progress survives because the package name and signing key (`~/.android/debug.keystore`) stay the same | The main repo is private, so the feed is a separate public repo with nothing but the APK. Needs the "install unknown apps" permission; Google Play Protect may block the install (see limitations) |
| 2026-10-18 | Home screen has a compact form below 720 dp of safe height (smaller calendar cells, no legend, no mode instruction line, tighter padding); screen-fit test added over a table of phone sizes | "Fits without scrolling" must hold on small phones too, and a test now guards it |
| 2026-10-18 | Home screen compacted so the calendar, daily goal and all three mode cards fit without scrolling: each mode card is a single row (title, instruction and counts on the left, Study button on the right), tighter card padding and spacing | Everything on the home screen should be visible at a glance; the scroll container stays only as a fallback for very small screens |
| **Der · Die · Das** | Pick the article of a noun | 484 nouns |
| **Prepositions** | Pick the missing preposition in a sentence (verbs, adjectives, nouns and set expressions with prepositions) | 309 items |
| **Plural forms** | Pick the plural of a noun | the same 484 nouns |
| **Präteritum** | Pick the er/sie/es Präteritum of a verb | 253 inseparable verbs (strong and mixed first, plus modals and frequent regular verbs) |

Each question is multiple choice. Cards come back according to a spaced-repetition scheduler, so
wrong or slow answers return soon and well-known ones are pushed out to days, weeks and months.

Target: Android 8.0+ (`minSdk 26`), built with `compileSdk 37` / `targetSdk 36`.
Package: `dev.yuji.quiz`. Single `app` module, no backend, no network access except the update check (see the decision log, 2026-10-19).

## 2. Design goals (from the original brief)

1. **Multiple quiz modes** behind one engine, so a new mode is a small addition.
2. **Anki-style repetition** of questions.
3. **Content split by game mode**, limited to basic B2 vocabulary.
4. **Encapsulated, easily debuggable code**: small pure functions chained into pipelines, descriptive
   names, and explicit debug flags.

Everything below follows from these four points.

## 3. Architecture

```
app/src/main/java/dev/yuji/quiz
├── domain/    pure Kotlin: cards, progress, scheduler, grader, queue pipeline, session engine
├── content/   vocabulary models, distractor generation, card factory (vocabulary -> cards)
├── data/      persistence: progress store (JSON file), settings store, vocabulary source (assets)
├── debug/     DebugFlags, StudyLog channels
├── android/   thin Android adapters (logcat logger)
├── ui/        Compose screens and the ViewModel
├── StudyController.kt   the single facade the UI talks to
└── MainActivity.kt
```

Dependency direction is one-way: `ui -> StudyController -> domain/content/data`. `domain` and
`content` have **no Android imports**, so all of their logic is covered by plain JVM unit tests
(no emulator needed).

### Key pieces

- **`Card`** is the unit of study: `id`, `mode`, `prompt`, optional `context` (the sentence),
  `choices`, `correctChoice`, `explanation`. Every mode produces the same shape, which is what
  makes the engine mode-agnostic.
- **`CardProgress`** is the per-card scheduling state (phase, learning step, ease factor,
  interval, due time, lapses, suspension/removal). It is immutable; every update returns a copy.
- **`SpacedRepetitionScheduler`** is a pure function `(progress, grade, now, config) -> progress`.
  It never reads the clock itself.
- **`AnswerGrader`** turns "correct? how fast? which phase?" into an Anki grade.
- **`StudyPipeline`** builds the queue as a chain of small steps (see below).
- **`StudyEngine` / `StudySession`** hold the running session as immutable state with pure transitions.
- **`StudyController`** wires store, clock, pipeline and engine together and is the only thing the
  UI calls.
- **`Clock`** is an injected interface so tests control time.

## 4. Spaced repetition

An SM-2 variant with Anki's default parameters (`SchedulerConfig`):

- Phases: `NEW -> LEARNING -> REVIEW`, with `RELEARNING` after a lapse.
- Learning steps 1 min, 10 min; graduating interval 1 day; easy interval 4 days.
- Starting ease 2.5, minimum 1.3, easy bonus 1.3, hard factor 1.2.
- A wrong answer on a review card is a **lapse**: relearning step (10 min), interval reset.
- Fully deterministic: **no interval fuzz**, so behaviour is reproducible in tests and debug.

### Grading multiple choice

Anki asks the user to self-grade. A quiz can't, so the grade is inferred (`AnswerGrader`):

| Answer | Grade |
|---|---|
| wrong | AGAIN |
| correct, 10 s or slower | HARD |
| correct, 2.5 s or faster, **card already in REVIEW** | EASY |
| otherwise | GOOD |

`EASY` is never given to cards still in learning, because a one-in-three guess on a fresh card
must not skip learning steps. Thresholds live in `GraderConfig`.

### Building the queue

`StudyPipeline.buildQueue` is a chain of independently tested steps:

`attachProgress -> excludeUnavailable -> selectLearningDue -> selectReviewsDue -> selectNewCards -> interleave`

- Learning cards are time-critical and always come first (with a 20-minute learn-ahead window).
- Reviews are most-overdue-first, capped at 100 per session.
- New cards: **no daily cap**; every unseen card is available and one is mixed in after every 3
  reviews, so the player can study as much as they want.

### Daily goal

A separate, purely motivational target (`DailyGoal.kt`, `TrainingStore`): a Settings slider from
**10 to 100 questions in steps of 10** (default 20). Every answer in **any** quiz counts toward
one shared per-day count (a re-shown learning card counts again). Counts are kept **per local
calendar day** as a history (`AnswerHistory`, epoch day -> answers, stored as `day:count;...` in
the `training` preferences); today's count is simply today's entry, so it restarts at zero after
midnight. Reaching the goal changes only the display ("Daily goal reached ✓"); nothing stops the
player from continuing.

### Streak calendar

A compact month calendar sits at the **top of the home screen**, above everything else (the
"Deutsch B2" title was dropped; the Settings button lives in the calendar's header row). It has
previous/next month arrows (no future months; the home screen always reopens on the current month),
Monday first, one small cell per day showing only the day number, and a one-line summary
("Streak n · longest m · d days this month"). A day with **at least one answer** counts as trained; the **current streak** is the run of
consecutive trained days ending today (an empty today does not break a streak that ran through
yesterday), plus the longest streak ever. Each trained day is coloured on a warm ramp
(`HeatColor.kt`): light yellow for a few answers -> orange -> orange-red at **100+ answers**
(the largest possible goal), with a thin legend bar underneath. Untrained days are neutral, today is outlined.
Pure logic is in `ActivityCalendar.kt`. The history is not touched by "Reset all progress" (that
resets cards only). Older installs migrate their single stored day count into the history.

## 5. Content

- Source of truth is two human-editable files: `tools/data/nouns.tsv` and
  `tools/data/prepositions.tsv`.
- `tools/build_vocab.py` validates them (structure, duplicates, allowed articles and cases) and
  writes the JSON the app bundles in `assets/vocab/`. It exits non-zero on any problem, so bad
  data cannot reach the app.
- `--verify-nouns` cross-checks the gender and plural of every noun against de.wiktionary.org.
  All 484 nouns were verified with no mismatches (the 166 B2 additions were checked the same way; *Ausdruck* was dropped because Wiktionary lists two plurals with different meanings, *Ausdrücke* and *Ausdrucke*, and the second would be an accidentally correct distractor).
- Nouns with several equally valid plurals are deliberately **left out** of the plural mode
  (or listed under "also valid"), so the quiz never marks a correct answer wrong.

### Hints for verbs with several prepositions

When the same headword appears with different prepositions (*sich freuen auf / über*,
*sich entschuldigen für / bei*, *bestehen aus / auf*, *sprechen über / mit*, *halten für / von*) the
sentence alone can be ambiguous, so each of those rows has a seventh TSV column, `hint`
(e.g. "looking forward to something in the future"). The app shows it under the headword as
"Hint: ...". `build_vocab.py` **fails** if a headword with several prepositions has a row without a
hint, so a new ambiguous pair cannot be added by accident. Because the hint now decides the
meaning, the rows are no longer each other's "also valid" alternative.

### Distractors

Wrong options must be plausible but never accidentally correct (`DistractorFactory`):

- **Prepositions:** drawn from a pool of common prepositions, excluding the answer and any
  alternative that is also valid.
- **Plurals:** generated from German plural patterns (`-e`, `-en`, `-er`, umlaut variants).
  `-n` is only offered after words ending in `-e`, and `-s` only after vowels, because
  "Tischn" or "Tischs" are obviously fake and would make the answer guessable.
- **Deterministic:** distractors are seeded from the entry, so a given card always shows the
  same options, which keeps tests and bug reports reproducible. The *order* of the buttons is
  shuffled per repetition but frozen while the answer feedback is on screen.

## 6. Persistence

- Progress is a single JSON file (`FileProgressStore`), rewritten **atomically** (temp file +
  rename) after every answer, so a crash or kill never leaves a half-written file.
- The store sits behind a `ProgressStore` interface; tests use `InMemoryProgressStore`.
- The theme choice is a separate, tiny settings store.
- A corrupt progress file is logged on the `STORAGE` channel and treated as empty rather than
  crashing the app.
- **Backup.** `ProgressBackup` (card states + answer history + daily goal + theme) is exported and imported
  as a JSON file via Android's document picker; `ProgressStore.replaceAll` applies an import in a single write.

## 7. Card management (suspend / remove)

- A **⋮** menu on each question: *Remove for 1 week*, *Remove for 1 month*, *Remove permanently*.
- Suspension is stored as `suspendedUntilMillis`; permanent removal as a `removed` flag. A card
  whose suspension has passed simply becomes available again, with no cleanup job.
- Restoring a card **keeps its learning history**; it resumes where it left off.
- The management list (counts, search, per-card Restore, reset-all with confirmation) is its
  **own screen**, opened from Settings, so Settings stays short.
- Search folds case and diacritics (`foldForSearch`), so `stuhle` finds *Stühle*.
- The ⋮ button is disabled during the half-second answer feedback, so a card cannot be
  suspended mid-transition.

## 8. UI

Jetpack Compose + Material 3, one `StudyViewModel` exposing a sealed `UiState`
(`ModeSelect`, `Studying`, `Finished`, `Settings`, `Failed`).

- Answer buttons use large text and a minimum 68 dp height (at the reference phone; scaled with screen height, see the decision log) for thumb use.
- Prepositions show the optional hint (italic, under the headword) and the sentence in a larger
  headline size.
- The correct answer turns green and a wrong pick red; the explanation shows under the question.
- The mode screen opens with a **Today: n / goal** card and progress bar; the study header shows
  the same count.
- The next question appears automatically **0.5 s** after an answer (no "Next" button).
- Theme: system default / light / night, remembered, with status and navigation bars following
  it (edge-to-edge).
- "Reset all progress" lives in Settings behind a confirmation dialog.

## 9. Debuggability

- `DebugFlags` is the single switch for diagnostics; debug builds enable everything, release
  builds nothing.
- `StudyLog` has typed channels: `CONTENT`, `PIPELINE`, `SCHEDULER`, `SESSION`, `STORAGE`, `UI`.
  Logging lambdas are lazy, so disabled channels cost nothing.
- A debug-build overlay under each question shows card id, grade, response time, phase
  transition, ease, interval, lapses and due time.

## 10. Testing

JVM unit tests cover the scheduler, grader, pipeline, availability/search, session engine,
content generation and an end-to-end controller test (including persistence across sessions).
`PeriodizationSimulationTest` follows one card through days of study with a fake clock (learning
steps, 1 -> 3 -> 8 -> 20 -> 50 day intervals, lapse and relearning, HARD/EASY timing). The same schedule
was verified on the phone by injecting due times into `progress.json`.
Every UI change is additionally exercised on a real phone (a Motorola edge 50 fusion over adb)
and the log is checked for crashes before a change is considered done.

**Screen-fit test** (`androidTest/.../ui/HomeScreenFitTest`, instrumented): composes the home screen
in a box of each phone's safe-area size (360x592 up to 448x890 dp, worst case a six-week calendar month)
and, for phones at least 800 dp tall, asserts the vertical scroll range is zero, i.e. nothing hides below the fold (shorter phones may scroll, so text stays readable). It runs on a device or
emulator because Robolectric has no real fonts and inflates text heights by about a third. Add a row to
its phone table when a new screen shape matters. `gradle.properties` keeps the APKs installed after the
run, otherwise AGP would uninstall the app and wipe the phone's progress.

Run: `source ~/dev/env.sh && ./gradlew testDebugUnitTest assembleDebug`;
screen fit (phone or emulator attached): `./gradlew connectedDebugAndroidTest`

## 11. Decision log

Newest first. Add an entry whenever a decision changes the design.

| Date | Decision | Why |
|---|---|---|
| 2026-10-19 | Android's Back button returns to the home screen from Settings, a study session and the finished screen (one `BackHandler` in `MainActivity`); on the home screen it leaves the app. The inner "Removed questions" page keeps its own handler, which takes priority | Back used to close the app from Settings, which lost the user's place and felt like a crash |
| 2026-10-19 | Progress backup: Settings > Backup exports one JSON file (`ProgressBackup`: format version, card states, answer history, daily goal, theme) and imports it back. Files go through Android's document picker (`CreateDocument` / `OpenDocument`), so the user picks local storage or Google Drive and the app needs no storage permission. Import replaces the current progress after a confirmation that shows the backup's date, card count and study days; entries with a mismatching card id, non-positive history counts and cards no longer in the deck are dropped, a file from a newer format is refused | Moving to a new phone (or reinstalling after a signing change) must not lose progress. Replace, not merge: simpler to explain and no conflict rules. The codec lives in `domain/` and is JVM-tested; the Android part is only the picker in `ui/BackupSection.kt` |
| 2026-10-19 | Own signing key: debug and release builds are both signed with `~/.android/german-quiz-release.jks` (credentials in `~/.android/german-quiz-signing.properties`, outside the repo; the build falls back to the default debug key if that file is missing). The feed now ships the minified, non-debuggable release APK. Update fetches add a query string (`?t=`, `?v=`) because raw.githubusercontent.com caches for 5 minutes | Same approach as Mihon (own persistent key, release build). Play Protect went from "Harmful app blocked" (debug key, debuggable) to the soft "App scan recommended: hasn't seen this app before". Losing the key means no in-place update, so it must be backed up |
| 2026-10-19 | In-app update from GitHub: on start and from Settings the app reads `latest.json` in the public repo `yujideluca/german-quiz-releases` (only the newest APK lives there; `tools/publish_apk.sh` builds, writes versionCode and sha256, and pushes). A higher versionCode shows a dialog; Update downloads, checks the SHA-256 and opens Android's installer. Progress survives because package name and signing key (`~/.android/debug.keystore`) stay the same | The main repo is private, so the feed is a separate public repo holding nothing but the APK. Needs the "install unknown apps" permission; Google Play Protect may block the install (see limitations) |
| 2026-10-19 | Noun pool grown from 318 to 484 and preposition pool from 175 to 309 with B2-exam material: abstract and argumentative nouns, work, health, law, media, environment; verb/adjective/noun + preposition pairs and fixed expressions (auf jeden Fall, in Kauf nehmen, ...). Where a second preposition is also correct in the sentence it is listed as "also valid" | Same principle as the verb pool: content useful for the B2 test; nouns re-verified against Wiktionary |
| 2026-10-19 | Präteritum pool grown from 136 to 253 verbs: B2-frequent prefixed strong/mixed verbs (be-, er-, ver-, ent-, über-, unter-) and other strong verbs first, plus common regular verbs. Excluded: reflexive-only verbs, verbs ending in -eln/-ern (the regularised distractor builder does not handle them), and verbs with two valid forms or two auxiliaries | The exam rewards the irregular verbs; the excluded ones would make the distractors wrong or the card ambiguous |
| 2026-10-19 | Screens scale with their height (`ui/ScreenScale.kt`): layout (paddings, spacing, calendar cells, answer-button height) = height / 880 dp capped to 0.8-1.2, text = same ratio capped to 1.0-1.15 (text never shrinks below normal); applied to home, question and finished screens, not Settings. Readability beats fitting: shorter phones scroll, and the screen-fit test now only requires a fit from 800 dp of height. Replaces the fixed compact sizes; the compact flag now only drops the legend and mode instruction lines below 820 dp | Elements should follow the phone size instead of two fixed sizes; text must stay readable, so a small phone scrolls rather than shrinking it |
| 2026-10-19 | Fourth mode, Präteritum (`tools/data/verbs.tsv` -> `verbs.json`, `VerbEntry`, `preteriteCard`); distractors are the regularised weak form (gehte), Partizip II, Präsens, infinitive; separable verbs left out; home screen compact form now below 820 dp with tighter spacing | A fourth card overflowed 5 of 7 phone sizes in the screen-fit test; separable verbs split in the Präteritum (er fing an) and don't fit a single-word choice |
| 2026-10-18 | Optional `hint` per preposition row, mandatory (build-checked) for headwords with several prepositions; sentence font enlarged; `kämpfen für` no longer accepts `gegen` | A sentence like "Wir freuen uns ___ den Urlaub" has two plausible answers without a clue; `gegen` would mean fighting against more rights |
| 2026-10-18 | Shuffled answer buttons are seeded with the repetition count from *before* the answer while feedback shows | Answering bumped the count, so Prepositions and Plural reshuffled the buttons under the finger and the green answer jumped |
| 2026-10-18 | Calendar made compact and moved to the top of the home screen; app title removed; separate calendar screen deleted | The streak should be visible at a glance without opening anything |
| 2026-10-18 | Streak calendar: per-day answer history, month view coloured light yellow -> orange-red by answers, streak = consecutive days with any answer | Visible training habit; colour shows effort, not just attendance |
| 2026-10-18 | Daily new-card cap (10) removed; replaced by a 10-100 daily-goal slider counting answers across all quizzes | The player should be able to study as much as they like; the goal is a target, not a limit |
| 2026-10-15 | Suspended/removed list and search moved to a separate screen opened from Settings | The list made Settings long and the keyboard hid the results |
| 2026-10-15 | Menu entries renamed "Remove for 1 week/month/permanently"; heading removed | The heading looked like a button and did nothing |
| 2026-10-15 | Suspend/remove, theme setting, reset-all confirmation added | Control over the pool; reset-all used to wipe everything on one tap |
| earlier | Auto-advance 0.5 s after an answer, bigger answer text | Faster drilling, easier thumb targets |
| earlier | Answer colours fixed (disabled-button colour was overriding red/green) | Right and wrong were indistinguishable |
| earlier | `-n`/`-s` plural distractors restricted to valid contexts | Obviously fake options made answers guessable |
| earlier | Replaced the general-knowledge quiz with three vocabulary modes | Brief: B2 German modes, Anki-style repetition |
| earlier | Kotlin + Compose chosen over Scala, Rust, Python, Elixir | Only first-class Android route; Compose is declarative and fits an immutable-state design |
| earlier | Deterministic scheduler, no fuzz | Reproducible behaviour for tests and debugging |
| earlier | Grade inferred from correctness and speed | Multiple choice has no self-assessment |
| earlier | Vocabulary kept as TSV + validating build script | Editable by hand, verified, bad data fails the build |

## 12. Known limitations and ideas

- Play Protect shows "App scan recommended" for an app it has not seen; the user must pick "Scan app" (or More details) once per new APK. Registering the package in the Android Developer Console (limited distribution account) might remove it; untested. The installer intent also shows an app chooser; `ACTION_INSTALL_PACKAGE` would avoid it.
- Progress view covers streaks only; no per-card mastery or accuracy history. A day's colour does not record which goal was set that day.
- The review cap per session (100) still applies; a finished session can simply be restarted.
- No custom app icon, accessibility pass, or tablet/landscape layout.
- The 10 s "hard" threshold may be strict for slow, deliberate answers.
- The explanation is visible for only 0.5 s; a longer delay after wrong answers is an option.

## How this file is maintained

- Updated **in the same commit** as any change that alters the architecture, a mode, the
  scheduler/grader behaviour, persistence or the UI flow. Add a row to the decision log and bump
  "Last updated".
- Reviewed periodically (at least once a month) to remove stale statements and refresh numbers
  such as pool sizes.
- `CLAUDE.md` in this repo tells Claude Code to do this automatically.

# German Quiz: design and development decisions

An offline Android app for drilling basic B2 German vocabulary with Anki-style spaced repetition.
This file explains **how the app is built** and **why** each choice was made. It is a living
document: see [How this file is maintained](#how-this-file-is-maintained).

Last updated: 2026-10-19

---

## 1. What the app is

Five study modes over a fixed, curated B2 pool (nothing beyond what every B2 speaker should know):

| Mode | Question | Pool |
|---|---|---|
| **Der · Die · Das** | Pick the article of a noun | 484 nouns |
| **Präpositionen** | Pick the missing preposition in a sentence (verbs, adjectives, nouns and set expressions with prepositions) | 309 items |
| **Pluralformen** | Pick the plural of a noun | the same 484 nouns |
| **Präteritum** | Pick the er/sie/es Präteritum of a verb | 253 inseparable verbs (strong and mixed first, plus modals and frequent regular verbs) |
| **Fehler finden** | Tap the one wrong part of a B2 writing sentence (forum post or formal e-mail) | 121 sentences, 12 error types |

Each question is multiple choice (in Fehler finden the "choices" are the sentence's chunks). Cards come back according to a spaced-repetition scheduler, so
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

- **Präpositionen:** drawn from a pool of common prepositions, excluding the answer and any
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
- The theme and language choices are a separate, tiny settings store (`SettingsStore`, SharedPreferences).
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

### Fehler finden (error spotting)

- Source: `tools/data/errors.tsv`, one row per sentence: `id|register|type|sentence|fix|rule|expression`.
  The sentence is written as 3-5 chunks separated by ` / `; the wrong chunk is wrapped in `{{ }}`.
  `build_vocab.py` fails unless exactly one whole chunk is marked, chunks are distinct, the fix differs
  from the wrong chunk and is not another chunk, and register and type are known.
- Every sentence practises a Redemittel, Nomen-Verb-Verbindung or fixed phrase from Giovanni's own lists
  (`expression` column: *Es stellt sich die Frage, ob …*, *Maßnahmen ergreifen*, *Bezug nehmen auf*, …) and
  contains **one** error of a kind Giovanni actually made in the corrected B2 mock exams and class texts of
  Oct 2026. Error types and sentence counts: verb position 16, word choice / collocation 16, verb or noun +
  preposition 15, article / adjective ending 13, preposition and case 13, passive (werden, not sein) 9,
  capitalisation / spelling 9, plural and gender 9, Sie vs. du 7, connectors 6, verb form 5, zu-infinitive 3.
- **Correctness checks.** Every corrected sentence must pass `tools/check_errors_languagetool.py` (LanguageTool, PICKY level) with no
  warning, and no warning may fall on a chunk other than the marked one. LanguageTool only recognises about a quarter of the planted errors,
  so that the marked chunk is really wrong, and the only wrong one, is checked by hand sentence by sentence. Rule of thumb used: an error
  counts only if a native speaker would not accept it in careful writing (so sein-Passiv sentences carry a process cue such as
  "jede Woche", and constructions that are merely colloquial or possible in speech, like *Bitte teilen Sie mir mit, falls …*, were removed).
- It reuses the normal `Card`: `choices` = the chunks in sentence order (`shuffleChoices = false`),
  `correctChoice` = the wrong chunk, `explanation` = the corrected sentence, the rule and the expression.
  Grading, scheduling and persistence are unchanged.

## 8. UI

Jetpack Compose + Material 3, one `StudyViewModel` exposing a sealed `UiState`
(`ModeSelect`, `Studying`, `Finished`, `Settings`, `Failed`).

- Answer buttons use large text and a minimum 68 dp height (at the reference phone; scaled with screen height, see the decision log) for thumb use.
- Präpositionen show the optional hint (italic, under the headword) and the sentence in a larger
  headline size.
- The correct answer turns green and a wrong pick red; the explanation shows under the question.
- Fehler finden lays the sentence out as tappable chunks (`ErrorSpotBody`, a `FlowRow`); after the answer the wrong
  chunk is green, a wrong tap red.
- The mode screen opens with a **Today: n / goal** card and progress bar; the study header shows
  the same count.
- The next question appears automatically **0.5 s** after an answer (no "Next" button), except in Fehler finden (`GameMode.autoAdvances = false`): its explanation is a sentence plus a rule, so the player reads it and taps **Next**.
- Theme: system default / light / night, remembered, with status and navigation bars following
  it (edge-to-edge).
- "Reset all progress" lives in Settings behind a confirmation dialog.

### Languages

Everything the app says itself is translated; the learning content is not. Questions, example sentences, answer options and
the corrected sentence stay German in every language.

- Languages: English, Deutsch, Português (Brasil), Español. Settings > Language lists them (each in its own name) after
  **Use system default**, which is the default: it follows the phone's language and falls back to English when the phone uses
  another one (`AppLanguage.effective`). The choice is stored in `SettingsStore.language`.
- UI text lives in Android string resources (`res/values/strings.xml` = English, `values-de`, `values-pt`, `values-es`). The
  mode names and instruction lines are resources too (`ui/ModeText.kt`), so "Prepositions" reads **Präpositionen** in German,
  **Preposições** in Portuguese and **Preposiciones** in Spanish. `domain/` and `content/` stay Android-free: `GameMode` only
  carries a developer `label`, and failures carry a kind (`BackupProblem`, `UpdateFailureKind`) that the UI maps to a string.
- Applying a language: `MainActivity.attachBaseContext` wraps the context with the stored language (`Context.withLanguage`,
  which also sets the JVM default locale for dates and month names), and the activity **recreates itself** when the choice
  changes. The `StudyViewModel` survives the recreation, so Settings stays open. Text the view model produces itself (backup
  notices, update errors) goes through its `t()` helper with the same language.
- The English prose inside the vocabulary data (the 12 preposition hints, the 12 error types and 116 rules of Fehler finden) is
  translated by `assets/i18n/<de|pt|es>.json`, keyed by the English original (`ContentTranslations`). Text without an entry is
  shown as written, so a new rule only needs a translation added to the three files. `ContentTranslationsTest` fails when
  one is missing; `StringResourcesTest` fails when a language lacks a string or its `%1$s` placeholders differ.

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
| 2026-10-19 | **Language setting.** Settings > Language: Use system default (default), English, Deutsch, Português (Brasil), Español. All app text except the German questions and answer options is translated (UI strings, mode names, update and backup messages, calendar month and weekday names, and the English explanation text in the vocabulary data via `assets/i18n/<lang>.json`). The activity recreates itself on change; see section 8 "Languages". Version 2.2 (13) | The app is used by people whose first language differs, and German-learning text should not be mixed with English menus when the learner prefers another language. Android string resources fell out naturally (plurals, formats, fallback to English), a per-app locale wrapper avoids needing AppCompat or Android 13's per-app language API (minSdk 26), and keying the data translations by the English original left `vocab/*.json` untouched, so the web version's vocabulary sync check is unaffected. Technical failure details such as a server's error text are still shown as the system gives them |
| 2026-10-19 | Mode names are German when the app language is German: **Prepositions** became **Präpositionen** and **Plural forms** became **Pluralformen** (first in `GameMode.title`, web `MODES`; since the language setting the titles are string resources, see the next row). Version 2.2 (13). Storage ids (`preposition`, `plural`) are unchanged, so saved progress and backups are not affected. The instruction lines under the titles stay English | All five mode names should read as German vocabulary the learner is drilling (Der · Die · Das, Präteritum, Fehler finden). Titles are display-only; the ids are what the scheduler and backups key on |
| 2026-10-19 | The app gets a **launcher icon**: an adaptive icon (version 2.1 (12); `mipmap-anydpi-v26/ic_launcher.xml`, background colour `#1A1118`, foreground `mipmap-xxxhdpi/ic_launcher_foreground.png`, 432 px) showing a Heighway dragon curve that runs from pastel pink to red. The image was rendered by the exported Processing app `drachenkurve` (`~/dev/processing-sketches/drachenkurve`, run with `DRACHEN_ICON=1`: 10 folding steps, square canvas); both flavors share it | Until now the app had no icon and showed Android's default. `minSdk 26` means adaptive icons are always available, so no per-density legacy PNGs are needed. The dragon sits inside the central 55 % so round and squircle masks do not cut it. The web version uses the same image for its icons (`QuizWeb/icons`) | 
| 2026-10-19 | Fifth mode, **Fehler finden** (`GameMode.ERROR_SPOT`, `tools/data/errors.tsv` -> `errors.json`, `ErrorSentenceEntry`, `CardFactory.errorCard`): 121 forum-post and formal-e-mail sentences, each with one wrong chunk to tap. Chunks are 1-5 words, so the tap target is a phrase, not a single word. Error types were taken from Giovanni's corrected writing (passive with sein, verb position, adjective endings, preposition + case, verb + preposition, du/Sie, capitalisation, plural/gender, collocations such as *Maßnahmen machen*) rather than from a generic list. Version 2.0 (11) | Giovanni's mistakes repeat in the same few patterns, and spotting them in a finished sentence trains the self-correction the Schreiben exam needs. Reusing the multiple-choice `Card` kept the scheduler, grader and backup untouched; only the screen differs. The mode does not auto-advance (a 0.5 s flash is too short to read a rule; a **Next** button replaces it), its new cards are shuffled once with a fixed seed so they do not arrive grouped by error type, and a fifth mode card overflowed the home screen on a 2400 px phone, so the compact home form now starts below 900 dp instead of 820 dp (instruction lines and calendar legend drop out) |
| 2026-10-19 | The update check runs **every time the app comes to the foreground** (`MainActivity.onStart` -> `StudyViewModel.onAppOpened`), at most once per 30 s (`shouldCheckForUpdate`), instead of only when the process is created. A newer version opens a popup on the home screen (never during a study session): Update downloads right away and the popup turns into "Updating…" with a progress bar until Android's installer opens; "Later" hides it only until the next open; a failed download offers "Try again"; the missing install permission has its own step. A known update stays on screen while the feed is asked again, and a failed check (offline, rate limit) never hides it (`stateWhileChecking` / `stateAfterCheck`) | The old behaviour checked once per process, so a phone that kept the app in memory never noticed a release, "Later" was remembered, and tapping Update gave no feedback. The popup and state rules are pure functions (`updatePromptFor`, `stateAfterCheck`) with unit tests |
| 2026-10-19 | Two release channels. The feed repo has two branches: `main` (stable, what users get) and `bleeding-edge` (testing). The build has two flavors: `stable` (`dev.yuji.quiz`, "Deutsch B2", follows `main`) and `beta` (`dev.yuji.quiz.beta`, "Deutsch B2 beta", version suffix `-beta`, follows `bleeding-edge`); both are signed with the same key and can be installed side by side with separate data. `tools/publish_apk.sh beta` publishes to bleeding-edge; `tools/publish_apk.sh promote` rebuilds the stable flavor and publishes it to `main` only if bleeding-edge holds that same versionCode and git commit (recorded as `commit` in `latest.json`) and the tree is clean. Promotion happens only when Giovanni says the beta is good | A broken build must never reach users: every change is first tried in the beta app, and the stable branch can only receive what was already published and tested there. A flavor (separate package) was chosen over a runtime switch so test data never mixes with the real progress; the cost is that promotion rebuilds from the same commit instead of copying the tested bytes |
| 2026-10-19 | The update client reads `latest.json` and the APK through the GitHub contents API (`Accept: application/vnd.github.raw+json`) instead of raw.githubusercontent.com. A 403 is reported as the hourly request limit (60 per IP without a token) | raw.githubusercontent.com caches for 5 minutes and ignores query strings (a random `?t=` still gave `x-cache: HIT`), so a fresh release stayed invisible and a stale manifest could meet a new APK (checksum failure). The API caches for at most 60 s and returned the new version immediately |
| 2026-10-19 | Android's Back button returns to the home screen from Settings, a study session and the finished screen (one `BackHandler` in `MainActivity`); on the home screen it leaves the app. The inner "Removed questions" page keeps its own handler, which takes priority | Back used to close the app from Settings, which lost the user's place and felt like a crash |
| 2026-10-19 | Progress backup: Settings > Backup exports one JSON file (`ProgressBackup`: format version, card states, answer history, daily goal, theme) and imports it back. Files go through Android's document picker (`CreateDocument` / `OpenDocument`), so the user picks local storage or Google Drive and the app needs no storage permission. Import replaces the current progress after a confirmation that shows the backup's date, card count and study days; entries with a mismatching card id, non-positive history counts and cards no longer in the deck are dropped, a file from a newer format is refused | Moving to a new phone (or reinstalling after a signing change) must not lose progress. Replace, not merge: simpler to explain and no conflict rules. The codec lives in `domain/` and is JVM-tested; the Android part is only the picker in `ui/BackupSection.kt` |
| 2026-10-19 | Own signing key: debug and release builds are both signed with `~/.android/german-quiz-release.jks` (credentials in `~/.android/german-quiz-signing.properties`, outside the repo; the build falls back to the default debug key if that file is missing). The feed now ships the minified, non-debuggable release APK. | Same approach as Mihon (own persistent key, release build). Play Protect went from "Harmful app blocked" (debug key, debuggable) to the soft "App scan recommended: hasn't seen this app before". Losing the key means no in-place update, so it must be backed up |
| 2026-10-19 | In-app update from GitHub: on start and from Settings the app reads `latest.json` in the public repo `yujideluca/german-quiz-releases` (only the newest APK lives there; `tools/publish_apk.sh` builds, writes versionCode and sha256, and pushes). A higher versionCode shows a dialog; Update downloads, checks the SHA-256 and opens Android's installer. Progress survives because package name and signing key (`~/.android/debug.keystore`) stay the same | The main repo is private, so the feed is a separate public repo holding nothing but the APK. Needs the "install unknown apps" permission; Google Play Protect may block the install (see limitations) |
| 2026-10-19 | Noun pool grown from 318 to 484 and preposition pool from 175 to 309 with B2-exam material: abstract and argumentative nouns, work, health, law, media, environment; verb/adjective/noun + preposition pairs and fixed expressions (auf jeden Fall, in Kauf nehmen, ...). Where a second preposition is also correct in the sentence it is listed as "also valid" | Same principle as the verb pool: content useful for the B2 test; nouns re-verified against Wiktionary |
| 2026-10-19 | Präteritum pool grown from 136 to 253 verbs: B2-frequent prefixed strong/mixed verbs (be-, er-, ver-, ent-, über-, unter-) and other strong verbs first, plus common regular verbs. Excluded: reflexive-only verbs, verbs ending in -eln/-ern (the regularised distractor builder does not handle them), and verbs with two valid forms or two auxiliaries | The exam rewards the irregular verbs; the excluded ones would make the distractors wrong or the card ambiguous |
| 2026-10-19 | Screens scale with their height (`ui/ScreenScale.kt`): layout (paddings, spacing, calendar cells, answer-button height) = height / 880 dp capped to 0.8-1.2, text = same ratio capped to 1.0-1.15 (text never shrinks below normal); applied to home, question and finished screens, not Settings. Readability beats fitting: shorter phones scroll, and the screen-fit test now only requires a fit from 800 dp of height. Replaces the fixed compact sizes; the compact flag now only drops the legend and mode instruction lines below 820 dp | Elements should follow the phone size instead of two fixed sizes; text must stay readable, so a small phone scrolls rather than shrinking it |
| 2026-10-19 | Fourth mode, Präteritum (`tools/data/verbs.tsv` -> `verbs.json`, `VerbEntry`, `preteriteCard`); distractors are the regularised weak form (gehte), Partizip II, Präsens, infinitive; separable verbs left out; home screen compact form now below 820 dp with tighter spacing | A fourth card overflowed 5 of 7 phone sizes in the screen-fit test; separable verbs split in the Präteritum (er fing an) and don't fit a single-word choice |
| 2026-10-18 | Home screen has a compact form below 720 dp of safe height (smaller calendar cells, no legend, no mode instruction line, tighter padding); screen-fit test added over a table of phone sizes | "Fits without scrolling" must hold on small phones too, and a test now guards it |
| 2026-10-18 | Home screen compacted so the calendar, daily goal and all three mode cards fit without scrolling: each mode card is a single row (title, instruction and counts on the left, Study button on the right), tighter card padding and spacing | Everything on the home screen should be visible at a glance; the scroll container stays only as a fallback for very small screens |
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
- No accessibility pass or tablet/landscape layout. The web version is not translated yet (English only).
- The 10 s "hard" threshold may be strict for slow, deliberate answers.
- The explanation is visible for only 0.5 s; a longer delay after wrong answers is an option.

## How this file is maintained

- Updated **in the same commit** as any change that alters the architecture, a mode, the
  scheduler/grader behaviour, persistence or the UI flow. Add a row to the decision log and bump
  "Last updated".
- Reviewed periodically (at least once a month) to remove stale statements and refresh numbers
  such as pool sizes.
- `CLAUDE.md` in this repo tells Claude Code to do this automatically.

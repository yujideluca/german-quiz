# German Quiz: design and development decisions

An offline Android app for drilling basic B2 German vocabulary with Anki-style spaced repetition.
This file explains **how the app is built** and **why** each choice was made. It is a living
document: see [How this file is maintained](#how-this-file-is-maintained).

Last updated: 2026-10-18

---

## 1. What the app is

Three study modes over a fixed, curated B2 pool (nothing beyond the words every B2 speaker should know):

| Mode | Question | Pool |
|---|---|---|
| **Der · Die · Das** | Pick the article of a noun | 318 nouns |
| **Prepositions** | Pick the missing preposition in a sentence (verbs, adjectives, nouns and set expressions with prepositions) | 175 items |
| **Plural forms** | Pick the plural of a noun | the same 318 nouns |

Each question is multiple choice. Cards come back according to a spaced-repetition scheduler, so
wrong or slow answers return soon and well-known ones are pushed out to days, weeks and months.

Target: Android 8.0+ (`minSdk 26`), built with `compileSdk 37` / `targetSdk 36`.
Package: `dev.yuji.quiz`. Single `app` module, no backend, no network access at runtime.

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
one shared per-day tally (a re-shown learning card counts again). The tally is keyed by local
calendar day and resets on the first answer after midnight. Reaching the goal changes only the
display ("Daily goal reached ✓"); nothing stops the player from continuing.

## 5. Content

- Source of truth is two human-editable files: `tools/data/nouns.tsv` and
  `tools/data/prepositions.tsv`.
- `tools/build_vocab.py` validates them (structure, duplicates, allowed articles and cases) and
  writes the JSON the app bundles in `assets/vocab/`. It exits non-zero on any problem, so bad
  data cannot reach the app.
- `--verify-nouns` cross-checks the gender and plural of every noun against de.wiktionary.org.
  All 318 nouns were verified with no mismatches.
- Nouns with several equally valid plurals are deliberately **left out** of the plural mode
  (or listed under "also valid"), so the quiz never marks a correct answer wrong.

### Distractors

Wrong options must be plausible but never accidentally correct (`DistractorFactory`):

- **Prepositions:** drawn from a pool of common prepositions, excluding the answer and any
  alternative that is also valid.
- **Plurals:** generated from German plural patterns (`-e`, `-en`, `-er`, umlaut variants).
  `-n` is only offered after words ending in `-e`, and `-s` only after vowels, because
  "Tischn" or "Tischs" are obviously fake and would make the answer guessable.
- **Deterministic:** distractors are seeded from the entry, so a given card always shows the
  same options, which keeps tests and bug reports reproducible.

## 6. Persistence

- Progress is a single JSON file (`FileProgressStore`), rewritten **atomically** (temp file +
  rename) after every answer, so a crash or kill never leaves a half-written file.
- The store sits behind a `ProgressStore` interface; tests use `InMemoryProgressStore`.
- The theme choice is a separate, tiny settings store.
- A corrupt progress file is logged on the `STORAGE` channel and treated as empty rather than
  crashing the app.

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

- Answer buttons use large text and a minimum 68 dp height for thumb use.
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
Every UI change is additionally exercised on a real phone (a Motorola edge 50 fusion over adb)
and the log is checked for crashes before a change is considered done.

Run: `source ~/dev/env.sh && ./gradlew testDebugUnitTest assembleDebug`

## 11. Decision log

Newest first. Add an entry whenever a decision changes the design.

| Date | Decision | Why |
|---|---|---|
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

- No progress view (streaks, per-card mastery, history).
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

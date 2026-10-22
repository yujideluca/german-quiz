# Requirements catalogue

Every row is a testable promise of the app. The ID is the key used everywhere else: tests carry the tag
`REQ <ID>` in a comment, and `tools/check_traceability.py` fails the quality gate when a requirement has
no test or a test names an unknown requirement. Source column: **G** = stated by Giovanni, **D** = design
decision in `DESIGN.md`. Level: **U** unit, **I** integration (controller with stores and a fake clock),
**P** property/randomised, **C** cross-platform conformance (app and web run the same vectors),
**A** acceptance on a device, **M** mutation-checked.

| ID | Requirement | Src | Level |
|---|---|---|---|
| SP-1 | A new card answered correctly is next due exactly 3 days later, never earlier. | G | U I P C M |
| SP-2 | A wrong answer makes the card due exactly 10 minutes later and never earlier: it is not re-queued in the same session and is not offered when the player leaves the quiz and returns before the 10 minutes are over; after that it is the first card. | G | U I P C M |
| SP-3 | Every further correct answer on a review card makes the interval strictly longer than the previous one. | G | U P C M |
| SP-4 | A wrong answer on a review card is a lapse: relearning (10 min), ease falls by 0.20 but never below 1.3, interval resets. | D | U C M |
| SP-5 | Intervals never exceed 36 500 days; ease never drops below 1.3. | D | U C |
| SP-6 | Scheduling is deterministic and pure (same input, same output; no clock access, no fuzz). | D | U P |
| GR-1 | Grading: wrong = AGAIN; correct and slow (>= 10 s) on a review card = HARD; correct and fast (<= 2.5 s) on a review card = EASY; otherwise GOOD. HARD and EASY are never given to cards still in (re)learning: a correct answer there always passes, however long the player thought. | D | U C M |
| QU-1 | Queue order: learning cards whose wait is over first, then due reviews (most overdue first, max 100), with one new card after every 3 reviews. Cards not yet due, suspended or removed are never queued. | D | U I P M |
| QU-2 | There is no daily cap on new cards; the daily goal is only a target. | D | U I |
| SE-1 | Invalid, duplicate or out-of-turn answers and advances are ignored. | D | U |
| SE-2 | Answer buttons keep their order while feedback is showing; the choices are always a permutation of the card's choices. | D | U |
| SE-3 | Every mode starts from the same state: all cards new, none due. | G | I P |
| PE-1 | Progress is saved after every answer and survives an app restart. | D | I |
| PE-2 | A damaged progress file is never silently destroyed: it is kept as a copy before anything new is written. | D | U |
| PE-3 | Backups round-trip; foreign, newer or damaged files are rejected with a readable reason; untrustworthy entries are dropped. | D | U I |
| PE-4 | "Reset all progress" clears cards only, not the training history. | D | I |
| DG-1 | Daily goal is 10..100 in steps of 10; answers in any mode count toward one per-day total that restarts at midnight. | D | U I |
| DG-2 | The progress bar shows today's answers divided by the daily goal: empty with no answers, proportional in between, full from the moment the goal is reached, never below empty, above full or undefined; the "goal reached" text appears exactly when the bar is full. | D | U P |
| DG-3 | Every accepted answer, right or wrong, in any mode raises today's count by exactly one. Rejected or repeated answers, removing or restoring a card, and resetting card progress leave it unchanged. | D | U P I |
| DG-4 | The bar stays consistent when things around it change: a new goal rescales it at once and keeps the count; the count survives a restart; a new day starts empty; a restored backup replaces count and goal; damaged stored values fall back to safe ones. | D | U P I |
| DG-5 | What the player sees equals the computed value: the bar's filled share and the "answered / goal" text show the same numbers on the phone and in the web version, for the same inputs (shared vectors). | D | C A |
| CA-1 | Streak and calendar: a day with at least one answer is trained; an empty today does not break a streak; longest streak and month totals are correct. | D | U I |
| MG-1 | Suspending (week, month, permanent), restoring and searching cards work; suspension ends on time and survives restart. | D | U I |
| LG-1 | Languages: the default follows the phone and falls back to English; an explicit choice wins; every string exists in every language with identical placeholders; only questions and answer options stay German. | G | U |
| CT-1 | Content integrity: unique card ids, exactly one correct choice among the offered choices, distractors never valid answers, expected pool sizes in every mode. | D | U A |
| UP-1 | Updates: only a higher version code is offered; a study session is never interrupted; a failed check never hides a known update; a checksum mismatch discards the download. | D | U |
| QP-1 | The question pool can grow without an app update: the app downloads a newer pack from its own feed branch (beta app: `bleeding-edge`, stable app: `main`) and uses it only when its format (schema) is the one the app reads and its version is higher than the pack in use, including the pack built into the app; otherwise the built-in pack is used. | G | U I |
| QP-2 | A downloaded pack is installed only when every file is listed, matches its SHA-256, parses, and every mode keeps at least one question. A refused, failed or offline download never changes or empties the pool in use, and an unreadable installed pack falls back to the built-in one. | D | U I M |
| QP-3 | A pool change keeps all progress (cards keep their ids; new questions start new), never interrupts a running study session (it is applied on the home screen), and is announced once on the home screen with the number of questions added. | G | U I |
| QP-4 | The built-in pack's `content.json` lists the SHA-256 of every bundled vocabulary and translation file, and its version rises whenever any of them changes. | D | U |
| UI-1 | The home screen shows calendar, goal and every mode card without scrolling on phones >= 800 dp tall. | D | A |
| WB-1 | The web version behaves like the user version: same scheduler (conformance vectors), same content, same strings, same service-worker cache version policy. | G | C |

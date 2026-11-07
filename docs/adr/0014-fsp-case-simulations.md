# ADR-0014: FSP case simulations as a sixth mode, one card per case, built from one fixed template

- **Status:** accepted
- **Date:** 2026-11-06
- **Type (ISO/IEC/IEEE 14764):** perfective
- **Decided by:** Giovanni (owner) / proposed by Claude Code

## Context

After the Goethe B2 exam Giovanni prepares the Fachsprachprüfung (FSP): the medical German exam of the
Landesärztekammer Baden-Württemberg. It is a 60-minute oral and written simulation in three parts of about 20 minutes:
an Arzt-Patienten-Gespräch (history taking with an actor patient), the Dokumentation (an Arztbrief written from the
history) and an Arzt-Arzt-Gespräch (handing the patient over to a senior doctor, ending with a list of medical terms
to explain in lay German). Only language is graded, not medical knowledge. Giovanni studies alone and asked for one
consistently built simulation per case "reported to be asked in Baden-Württemberg", ordered by area of medicine,
in which he picks the right thing to say.

Research on 2026-11-06 (six parallel web searches in German, English, Arabic, Turkish, Russian and other languages)
found no public exam report naming a Baden-Württemberg case; the reports circulate in closed groups. Giovanni chose
"BW reports + fill": every case found in BW reports, filled with the classic FSP cases. With no BW report found, the
case list (`tools/data/fsp_cases.tsv`, 58 cases in 12 areas) consists of classic FSP cases, each tagged `common_FSP`
so a later BW report can be recorded (`reported_BW`, `bwReports`).

## Decision

1. **A new mode `FSP_CASE` ("FSP-Fälle").** A case is one `Card` (`fsp:<id>`) in the existing scheduler, so the
   spacing rules SP-1 to SP-4 apply to whole cases. The card's two "choices" are *bestanden* / *nicht bestanden*.
2. **Grading from the score, never from time (FS-4).** A step counts as right only when the first pick was right.
   Score = first-try right steps / all steps. At least 80 % passes (GOOD; on a review card at least 95 % is EASY and
   below 90 % HARD); below 80 % is AGAIN. `StudyEngine.answer` takes a `gradeOverride` for this; it refuses a grade
   that contradicts right/wrong.
3. **One fixed template (FS-1).** Every case has 34 steps in the same order and with the same section names: part 1
   16 steps (greeting to explaining the next steps), part 2 7 steps (the Arztbrief), part 3 6 steps (handover) plus 5
   Fachbegriffe. Every step: 4 answers, exactly one right, a German explanation for each. `tools/check_fsp_cases.py`
   enforces this, plus: the title never names the diagnosis (FS-5), and the right answer may be the longest option in at
   most 60 % of the steps, so tapping the longest answer does not pass. The check runs in `build_vocab.py` and in the
   quality gate.
4. **Data:** one JSON file per case in `tools/data/fsp/`, built into `assets/vocab/fsp_cases.json` in list order
   (FS-2: ordered by area of medicine). The case texts are German only (the exam language); only the screen labels are
   translated. The pack format becomes schema 2 (`CONTENT_SCHEMA`, `content_manifest.py`); older apps ignore schema-2
   packs and keep their built-in pool until they update.
5. **Authoring:** one hand-written reference case (`akute-cholezystitis.json`); the other 57 were written by one
   Sonnet agent each against the reference and the validator, then reviewed and corrected by a second Sonnet agent
   acting as a strict German physician and language examiner.
6. **A case reads like a script (FS-6, amended 2026-11-07 at Giovanni's request).** The first version asked a quiz
   question per step ("Wie fragen Sie nach …?"). Now a step never asks: its `situation` narrates what the patient or the
   Oberarzt answers and does in reply to the player's last line (shown above it), or what the player is writing in
   part 2. The reply to a step is the next step's situation, so `reply` is kept only on the last step of each part;
   `question` is gone. Without the question, each step must still have exactly one clearly right answer from the scene
   alone: cues in the scene or wrong options that are wrong in themselves (register, suggestive, rude, medically
   wrong). The validator refuses a `?` outside „…“ speech and a reply inside a part. The pack format becomes schema 3,
   so 2.8.0 apps keep their schema-2 pool.
7. **Each step counts for the day (FS-7).** The scheduler sees one card per case, but the daily goal and the streak
   calendar count every solved step as one answered question (`StudyController.pickInCase`); the graded case adds none.

## Alternatives considered

- **Each step as its own card** — the scheduler would drill single phrases out of context; the FSP is a whole
  conversation, and Giovanni asked to simulate the whole exam.
- **Free choice of cases without scheduling** — offered; Giovanni chose scheduling (case = one card).
- **Branching cases** — offered; more realistic but much harder to keep consistent and to test. Linear with retries
  was chosen.
- **Explanations in the app language** — about 7 000 explanation strings in three more languages; German keeps the
  immersion of the exam and avoids translation drift.

## Consequences

- Content quality is now the main risk: medical and language errors are possible in agent-written text. The
  template check catches structure, not meaning; Giovanni's reports of wrong content are fixed in the case file and
  shipped as a pool update (ADR-0012), without an app release.
- The web twin needs the same mode (ADR-0006).
- Tests: `FspCaseTest` (template, order, run, score, grading), `FspCaseControllerTest` (order of new cases, 3 days after a
  pass, 10 minutes after a fail, no time effect, review grading), mutants in `tools/mutation_check.py`.

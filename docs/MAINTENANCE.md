# Maintenance and evolution

How the app is kept healthy as it grows. The method follows the current mainstream practice:
the maintenance types of **ISO/IEC/IEEE 14764**, **Lehman's laws** (software in use must keep changing, and its
complexity grows unless work is spent to reduce it), **evolutionary architecture** (architecture rules as automatic
checks, called fitness functions) and the **DORA** delivery metrics.

## 1. Where things are written

| What | Where | Kept true by |
|---|---|---|
| Every design change, one line, with its maintenance type | `DESIGN.md` section 11 | `CLAUDE.md` rule; monthly review |
| Structural decisions with alternatives and consequences | `docs/adr/` | `CLAUDE.md` rule; monthly review |
| Architecture picture (C4 context and modules) | `DESIGN.md` section 3 (Mermaid) | changes in the same commit as the code |
| Behaviour the app promises | `docs/REQUIREMENTS.md` | traceability gate |
| Architecture rules | `:core` module boundary, `tools/pipeline-check` (limits in `pipeline-check.toml`), web `tests/architecture.test.js` | compiler, quality gate, CI |
| Known debt and ideas | GitHub Issues with the label `debt` and a priority label (`P1`, `P2`, `P3`) | monthly review |

## 2. Technical debt

**Debt** here means anything that works today but makes the next change slower or riskier: a missing test, a
known limitation, a file doing too many jobs, a manual step that should be a script.

- Every known debt item is a GitHub Issue in this repo (or `german-quiz-web`) with the label `debt` and one
  priority: `P1` (risk of losing progress or teaching wrong answers), `P2` (slows every change or misleads the
  player), `P3` (nice to have). Ideas that are new features use the label `idea` instead.
- **Fixed share:** about one work session in five (≈ 20 %) goes to debt or prevention (ISO type *preventive*),
  starting with the highest priority. When Giovanni asks for a feature and the last four sessions were all features,
  Claude Code says so and proposes the top `debt` issue before or after it; Giovanni decides.
- A new limitation found while working becomes an issue in the same session, not only a line in a report.

## 3. Monthly architecture review

About once a month (open item in workflow-memory), one session with this checklist. Write the outcome as a
decision-log row of Type *Preventive* (or "no changes needed") and close or re-prioritise issues.

1. `tools/quality_gate.sh` passes locally; the last CI runs on GitHub are green (including Monday's mutation run).
2. `python3 tools/metrics.py --days 30`: note deployment frequency, lead time, rework rate, preventive share. Ask:
   - Is the rework rate rising? Then a requirement or test is missing somewhere (ADR-0008).
   - Is the preventive share far below 20 %? Then schedule the top `debt` issues.
   - Time in beta is not a warning sign (ADR-0011: the updated tests are the condition, then Giovanni decides).
3. `tools/pipeline-check.sh` (writes `build/pipeline-check/report.json`; the skill `revisao-manutencao` reads it): which check is in `aviso`, e.g. the file closest to 400 lines? Act before it crosses.
4. Do `DESIGN.md` section 3 (diagrams, tree) and the ADRs still match the code? Is every structural change since the
   last review covered by an ADR?
5. Adaptive maintenance: newer Android Gradle Plugin, Kotlin, Compose BOM, `targetSdk` required by Android? Upgrade
   on a branch, run the gate, publish to beta.
6. Debt list: still true, still the right priority? Anything P1 open for more than a month gets scheduled now.
7. Memory: are workflow-memory entries about this project still true (`mem search quiz`)?

## 4. Metrics

`tools/metrics.py` reads only what already exists: the feed repo history (releases), git (commit times) and the
decision log (types). Time to restore after a failure is not measured: when something breaks for the player, the
Corrective row says how long it lasted.

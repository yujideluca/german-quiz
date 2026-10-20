#!/usr/bin/env python3
"""Mutation check: do the tests notice planted faults?

Each mutant changes one constant, comparison or condition in the scheduler, grader, queue, session, store or
web twin. The matching test suite must FAIL for the mutant to count as killed. A surviving mutant means a
requirement is not really protected by a test. The source file is always restored afterwards.

usage: tools/mutation_check.py [--web-only|--app-only]
exit 0 = every mutant killed.
"""
import os, subprocess, sys, time

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
web = os.environ.get("QUIZWEB", os.path.expanduser("~/dev/QuizWeb"))
D = "app/src/main/java/dev/yuji/quiz/"

# (file, exact text, replacement, what the fault means)
APP = [
 (D+"domain/SchedulerConfig.kt", "val learningStepsMinutes: List<Int> = listOf(10)", "val learningStepsMinutes: List<Int> = listOf(1)", "missed new card returns after 1 min"),
 (D+"domain/SchedulerConfig.kt", "val relearningStepsMinutes: List<Int> = listOf(10)", "val relearningStepsMinutes: List<Int> = listOf(1)", "lapsed card returns after 1 min"),
 (D+"domain/SchedulerConfig.kt", "val graduatingIntervalDays: Int = 3", "val graduatingIntervalDays: Int = 1", "first correct answer -> 1 day"),
 (D+"domain/SchedulerConfig.kt", "val learnAheadMinutes: Int = 0", "val learnAheadMinutes: Int = 20", "learn-ahead window is back"),
 (D+"domain/SchedulerConfig.kt", "val minimumEase: Double = 1.3", "val minimumEase: Double = 1.0", "ease floor lowered"),
 (D+"domain/SchedulerConfig.kt", "val maximumIntervalDays: Int = 36_500", "val maximumIntervalDays: Int = 365_000", "interval cap raised"),
 (D+"domain/SchedulerConfig.kt", "val startingEase: Double = 2.5", "val startingEase: Double = 2.0", "intervals grow slower"),
 (D+"domain/SpacedRepetitionScheduler.kt", "lapses = p.lapses + 1", "lapses = p.lapses", "lapses not counted"),
 (D+"domain/SpacedRepetitionScheduler.kt", "p.easeFactor - 0.20", "p.easeFactor - 0.0", "lapse keeps ease"),
 (D+"domain/SpacedRepetitionScheduler.kt", "ReviewGrade.GOOD -> due(p, grow(p.easeFactor, old + 1), now)", "ReviewGrade.GOOD -> due(p, grow(p.easeFactor, old), now)", "GOOD may not lengthen the gap"),
 (D+"domain/SpacedRepetitionScheduler.kt", "if (step + 1 >= steps.size) graduate(c.graduatingIntervalDays)", "if (step + 2 >= steps.size) graduate(c.graduatingIntervalDays)", "learning step off by one"),
 (D+"domain/AnswerGrader.kt", "responseMillis >= config.hardMinMillis", "responseMillis > config.hardMinMillis", "HARD boundary moved"),
 (D+"domain/AnswerGrader.kt", "responseMillis <= config.easyMaxMillis", "responseMillis < config.easyMaxMillis", "EASY boundary moved"),
 (D+"domain/AnswerGrader.kt", "phase == CardPhase.REVIEW && responseMillis >= config.hardMinMillis", "responseMillis >= config.hardMinMillis", "slow correct answer on a new card counts as HARD"),
 (D+"domain/AnswerGrader.kt", "responseMillis <= config.easyMaxMillis && phase == CardPhase.REVIEW", "responseMillis <= config.easyMaxMillis", "EASY allowed while learning"),
 (D+"domain/StudyPipeline.kt", "it.progress.phase == CardPhase.REVIEW && it.progress.dueAtMillis <= nowMillis", "it.progress.phase == CardPhase.REVIEW && it.progress.dueAtMillis < nowMillis", "due review offered a millisecond late"),
 (D+"domain/StudyPipeline.kt", "it.progress.dueAtMillis <= nowMillis + learnAheadMinutes * MINUTE_MILLIS", "true", "waiting learning cards offered early"),
 (D+"domain/StudyPipeline.kt", "(i + 1) % every == 0", "(i + 1) % (every + 1) == 0", "new cards interleaved at the wrong rhythm"),
 (D+"domain/StudyPipeline.kt", "val learning = selectLearningDue(items, nowMillis, config.learnAheadMinutes)", "val learning = emptyList<CardWithProgress>()", "learning cards never offered"),
 (D+"domain/StudySession.kt", "after.dueAtMillis <= nowMillis + schedulerConfig.learnAheadMinutes * MINUTE_MILLIS", "true", "missed card queued again in the same session"),
 (D+"domain/StudySession.kt", "if (session.isAwaitingAdvance || choice !in card.choices) return session", "if (choice !in card.choices) return session", "double answers accepted"),
 (D+"domain/StudySession.kt", "if (session.isAwaitingAdvance || choice !in card.choices) return session", "if (session.isAwaitingAdvance) return session", "unknown choices accepted"),
 (D+"domain/DailyGoal.kt", "coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)", "coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL + 10)", "daily goal above 100"),
 (D+"domain/AppUpdate.kt", "nowMillis - lastCheckMillis >= minGapMillis", "nowMillis - lastCheckMillis > minGapMillis", "update check gap boundary"),
 (D+"data/ProgressStore.kt", "                    keepDamagedCopy()\n", "", "damaged progress file not preserved"),
]
WEB = [
 ("src/domain.js", "learningStepsMinutes: [10]", "learningStepsMinutes: [1]", "web: missed card returns after 1 min"),
 ("src/domain.js", "graduatingIntervalDays: 3", "graduatingIntervalDays: 1", "web: first correct answer -> 1 day"),
 ("src/domain.js", "learnAheadMinutes: 0", "learnAheadMinutes: 20", "web: learn-ahead window is back"),
 ("src/domain.js", "phase === 'REVIEW' && responseMillis >= config.hardMinMillis", "responseMillis >= config.hardMinMillis", "web: slow correct answer on a new card is HARD"),
 ("src/domain.js", "easyIntervalDays: 5", "easyIntervalDays: 4", "web: easy interval drift"),
]

def run(cmd, cwd):
    return subprocess.run(cmd, cwd=cwd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

def check(base, rows, test_cmd, label):
    survived = []
    for path, old, new, why in rows:
        full = os.path.join(base, path)
        src = open(full, encoding="utf-8").read()
        if src.count(old) < 1:
            print(f"  [{label}] MUTANT NOT APPLICABLE (text changed?): {path}: {old[:50]!r}")
            survived.append((path, why + " (not applicable)")); continue
        try:
            open(full, "w", encoding="utf-8").write(src.replace(old, new, 1))
            t = time.time()
            r = run(test_cmd, base)
            killed = r.returncode != 0
            print(f"  [{label}] {'killed  ' if killed else 'SURVIVED'} {why}  ({time.time() - t:.0f}s)")
            if not killed:
                survived.append((path, why))
        finally:
            open(full, "w", encoding="utf-8").write(src)
    return survived

mode = sys.argv[1] if len(sys.argv) > 1 else ""
survivors, total = [], 0
if mode != "--web-only":
    survivors += check(root, APP, "bash -c 'source ~/dev/env.sh && ./gradlew testStableDebugUnitTest -q'", "app"); total += len(APP)
if mode != "--app-only" and os.path.isdir(web):
    survivors += check(web, WEB, "npm test --silent", "web"); total += len(WEB)
print(f"mutation score: {total - len(survivors)}/{total} killed")
for p, why in survivors:
    print("  survived:", p, "-", why)
sys.exit(1 if survivors else 0)

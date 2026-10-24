#!/usr/bin/env bash
# Quality gate = the exit criteria of docs/TESTING.md. publish_apk.sh runs it before anything is published.
#   tools/quality_gate.sh              all checks except the slow mutation check
#   tools/quality_gate.sh --mutation   also plant faults and demand that the tests catch every one (used by `promote`)
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f ~/dev/env.sh ] && source ~/dev/env.sh   # laptop toolchain; CI sets up its own
web="${QUIZWEB:-$HOME/dev/QuizWeb}"
MAX_GATE_SECONDS=1200   # fitness function: a gate that gets slow gets skipped; split or speed up tests instead
SECONDS=0
step() { printf '\n== %s\n' "$1"; }

step "1/7 core and app unit, property, conformance and integration tests + coverage"
./gradlew :core:test testStableDebugUnitTest createStableDebugUnitTestCoverageReport -q

step "2/7 coverage thresholds (logic packages)"
python3 tools/check_coverage.py

step "3/7 web tests"
(cd "$web" && npm test --silent)

step "4/7 conformance vectors identical in app and web"
for f in scheduler daily_bar; do cmp "core/src/test/resources/conformance/$f.json" "$web/tests/conformance/$f.json"; done

step "5/7 requirement traceability"
python3 tools/check_traceability.py
git diff --quiet -- docs/TRACEABILITY.md || { echo "docs/TRACEABILITY.md changed: commit the regenerated matrix"; exit 1; }

step "6/7 Android lint (errors fail the gate)"
./gradlew lintStableDebug -q

step "7/7 fitness functions (pure core module, file size) and the question pool manifest"
tools/pipeline-check.sh --measure-only
python3 tools/content_manifest.py --check
[ "$SECONDS" -le "$MAX_GATE_SECONDS" ] || { echo "FAIL gate took ${SECONDS}s (> ${MAX_GATE_SECONDS}s)"; exit 1; }
echo "gate time so far: ${SECONDS}s (limit ${MAX_GATE_SECONDS}s)"

if [ "${1:-}" = "--mutation" ]; then
  step "mutation check (faults planted on purpose must be caught)"
  python3 tools/mutation_check.py
fi
printf '\nquality gate passed%s\n' "$([ "${1:-}" = "--mutation" ] && echo " (with mutation check)")"

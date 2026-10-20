#!/usr/bin/env bash
# Quality gate = the exit criteria of docs/TESTING.md. publish_apk.sh runs it before anything is published.
#   tools/quality_gate.sh              all checks except the slow mutation check
#   tools/quality_gate.sh --mutation   also plant faults and demand that the tests catch every one (used by `promote`)
set -euo pipefail
cd "$(dirname "$0")/.."
source ~/dev/env.sh
web="${QUIZWEB:-$HOME/dev/QuizWeb}"
step() { printf '\n== %s\n' "$1"; }

step "1/6 app unit, property, conformance and integration tests + coverage"
./gradlew testStableDebugUnitTest createStableDebugUnitTestCoverageReport -q

step "2/6 coverage thresholds (logic packages)"
python3 tools/check_coverage.py

step "3/6 web tests"
(cd "$web" && npm test --silent)

step "4/6 conformance vectors identical in app and web"
cmp app/src/test/resources/conformance/scheduler.json "$web/tests/conformance/scheduler.json"

step "5/6 requirement traceability"
python3 tools/check_traceability.py
git diff --quiet -- docs/TRACEABILITY.md || { echo "docs/TRACEABILITY.md changed: commit the regenerated matrix"; exit 1; }

step "6/6 Android lint (errors fail the gate)"
./gradlew lintStableDebug -q

if [ "${1:-}" = "--mutation" ]; then
  step "mutation check (faults planted on purpose must be caught)"
  python3 tools/mutation_check.py
fi
printf '\nquality gate passed%s\n' "$([ "${1:-}" = "--mutation" ] && echo " (with mutation check)")"

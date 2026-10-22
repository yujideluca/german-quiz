#!/usr/bin/env bash
# A new version may reach users only when it survived automated tests that were updated for what it changes
# (docs/adr/0011). This check compares the commit to publish with a base commit (the last stable release):
# if code or content changed and no test changed, the tests cannot have been updated for the new version.
#   tools/check_tests_updated.sh BASE_COMMIT     exit 0 = tests changed too (or nothing that needs a test changed)
#                                                exit 1 = code changed without any test change
set -euo pipefail
cd "$(dirname "$0")/.."
base="${1:?base commit required}"
if ! git cat-file -e "$base^{commit}" 2>/dev/null; then
  echo "check_tests_updated: base ${base:0:7} not in this repository; cannot compare (skipped)"; exit 0
fi
changed=$(git diff --name-only "$base" HEAD)
code=$(echo "$changed" | grep -E '^(core|app)/src/main/' || true)
tests=$(echo "$changed" | grep -E '^(core|app)/src/(test|androidTest)/' || true)
if [ -n "$code" ] && [ -z "$tests" ]; then
  echo "Code or content changed since ${base:0:7} but no test did:"
  echo "$code" | sed 's/^/  /'
  echo "Update the tests for what this version changes (requirement row, tagged test; docs/TESTING.md) first."
  exit 1
fi
echo "tests updated since ${base:0:7}: $(echo "$tests" | grep -c . || true) test file(s) for $(echo "$code" | grep -c . || true) changed source file(s)"

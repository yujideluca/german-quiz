#!/usr/bin/env bash
# Publishes the app to the public feed repo (yujideluca/german-quiz-releases), which has two branches:
#   bleeding-edge  the beta app ("Deutsch B2 beta", package dev.yuji.quiz.beta), for testing
#   main           the stable app ("Deutsch B2", package dev.yuji.quiz), what users receive
# Each branch holds only latest.json and the newest APK of its channel.
#
#   tools/publish_apk.sh beta     build the beta flavor and publish it to bleeding-edge
#   tools/publish_apk.sh promote  build the stable flavor and publish it to main, but only if the
#                                 version on bleeding-edge is this exact commit (so what was tested is what ships)
#
# A version may be promoted once it survives the automated tests updated for what it changes (docs/adr/0011):
# the gate with the mutation check, and tools/check_tests_updated.sh against the last stable release (code that
# changed without any test change is refused). From then on, promoting is Giovanni's decision; no waiting time.
#
# Both modes first run tools/quality_gate.sh (tests, coverage, traceability, conformance, lint; promote adds the mutation check).
# After a promote the script checks that the web version (https://yujideluca.github.io/german-quiz-web/) is in step with the app
# (tools/check_web_sync.sh) and exits 2 if it is not: the web version is updated every time the user version is.
#
# Bump versionCode/versionName in app/build.gradle.kts first: the app only offers an update when versionCode is
# higher than the installed one. The working tree must be committed. Keep the same signing key
# (~/.android/german-quiz-release.jks) or Android refuses to update in place.
set -euo pipefail
mode="${1:-}"
[ "$mode" = beta ] || [ "$mode" = promote ] || { sed -n '2,15p' "$0"; exit 1; }

source "$HOME/dev/env.sh"
root="$(cd "$(dirname "$0")/.." && pwd)"
feed="${FEED_DIR:-$HOME/dev/german-quiz-releases}"
cd "$root"

[ -z "$(git status --porcelain)" ] || { echo "commit your changes first (the feed records the commit)"; exit 1; }
commit=$(git rev-parse HEAD)
code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
name=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)

git -C "$feed" fetch -q origin
manifest() { git -C "$feed" show "origin/$1:latest.json" 2>/dev/null; }
field() { python3 -I -c 'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$1"; }

if [ "$mode" = beta ]; then
  branch=bleeding-edge; flavor=Beta; apk=app/build/outputs/apk/beta/release/app-beta-release.apk
else
  branch=main; flavor=Stable; apk=app/build/outputs/apk/stable/release/app-stable-release.apk
  tested=$(manifest bleeding-edge || true)
  [ "$(echo "$tested" | field versionCode)" = "$code" ] && [ "$(echo "$tested" | field commit)" = "$commit" ] \
    || { echo "refusing to promote: bleeding-edge does not hold version $code of commit ${commit:0:7}. Publish it there with 'beta' and test it first."; exit 1; }
fi

# Tests must have been updated for what changed since the last stable release (ADR-0011). Beta only warns,
# so the gap shows up before Giovanni is asked to promote.
stable_commit=$(manifest main | field commit || true)
if [ -n "$stable_commit" ]; then
  if [ "$mode" = promote ]; then tools/check_tests_updated.sh "$stable_commit"
  else tools/check_tests_updated.sh "$stable_commit" || echo "WARNING: promote will refuse this version until its tests are updated"; fi
fi

# Exit criteria of docs/TESTING.md; a promote also has to survive the mutation check.
tools/quality_gate.sh $([ "$mode" = promote ] && echo --mutation)
./gradlew "test${flavor}DebugUnitTest" "assemble${flavor}Release" -q
tools/pipeline-check.sh --measure-only --apk "$apk"   # release APK size limit

git -C "$feed" checkout -q "$branch" 2>/dev/null || git -C "$feed" checkout -q -b "$branch" origin/main
git -C "$feed" reset -q --hard "origin/$branch" 2>/dev/null || true
cp "$apk" "$feed/german-quiz.apk"
sha=$(sha256sum "$feed/german-quiz.apk" | cut -d' ' -f1)
printf '{\n  "versionCode": %s,\n  "versionName": "%s",\n  "apk": "german-quiz.apk",\n  "sha256": "%s",\n  "commit": "%s"\n}\n' "$code" "$name" "$sha" "$commit" > "$feed/latest.json"
git -C "$feed" add german-quiz.apk latest.json
git -C "$feed" commit -q -m "$mode $name (versionCode $code, ${commit:0:7})"
git -C "$feed" push -q origin "$branch"
echo "$mode: published $name ($code) to $branch sha256=$sha"
if [ "$mode" = promote ]; then "$root/tools/check_web_sync.sh"; fi

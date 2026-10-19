#!/usr/bin/env bash
# Builds the signed release APK and publishes it as the only APK in the public feed repo
# (yujideluca/german-quiz-releases). Bump versionCode/versionName in app/build.gradle.kts first:
# the app only offers an update when versionCode is higher than the installed one.
# The APK must keep the same signing key (~/.android/german-quiz-release.jks) or Android refuses to update in place.
set -euo pipefail
source "$HOME/dev/env.sh"
root="$(cd "$(dirname "$0")/.." && pwd)"
feed="${FEED_DIR:-$HOME/dev/german-quiz-releases}"

code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$root/app/build.gradle.kts")
name=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$root/app/build.gradle.kts")

(cd "$root" && ./gradlew testDebugUnitTest assembleRelease -q)
git -C "$feed" pull -q --ff-only
cp "$root/app/build/outputs/apk/release/app-release.apk" "$feed/german-quiz.apk"
sha=$(sha256sum "$feed/german-quiz.apk" | cut -d' ' -f1)
printf '{\n  "versionCode": %s,\n  "versionName": "%s",\n  "apk": "german-quiz.apk",\n  "sha256": "%s"\n}\n' "$code" "$name" "$sha" > "$feed/latest.json"
git -C "$feed" add german-quiz.apk latest.json
git -C "$feed" commit -q -m "Release $name (versionCode $code)"
git -C "$feed" push -q origin HEAD
echo "published $name ($code) sha256=$sha"

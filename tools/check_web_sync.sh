#!/usr/bin/env bash
# The web version (https://yujideluca.github.io/german-quiz-web/, repo yujideluca/german-quiz-web, clone ~/dev/QuizWeb)
# must always match the user version of the Android app. Exits 2 and says what is behind when it does not:
#   - a vocabulary file (nouns, prepositions, verbs, errors) differs from the app's assets/vocab, or a translation file
#     (assets/i18n/de|pt|es.json) differs from the web's i18n/
#   - the set of modes differs (GameMode.kt storage ids vs MODES in the web's src/domain.js)
#   - the web clone has uncommitted work or commits that are not on GitHub yet
# tools/publish_apk.sh promote runs it at the end. Fix = port the change into ~/dev/QuizWeb (copy the vocab files, port code
# and tests, bump VERSION in sw.js), run `npm test`, commit and push; GitHub Pages then serves it within a minute.
set -uo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
web="${WEB_DIR:-$HOME/dev/QuizWeb}"
problems=()

for f in nouns prepositions verbs errors; do
  cmp -s "$root/app/src/main/assets/vocab/$f.json" "$web/vocab/$f.json" || problems+=("vocab/$f.json differs from the app's")
done

for f in de pt es; do
  cmp -s "$root/app/src/main/assets/i18n/$f.json" "$web/i18n/$f.json" || problems+=("i18n/$f.json differs from the app's")
done

app_modes=$(sed -n 's/^ *[A-Z_]*("\([a-z]*\)",.*/\1/p' "$root/core/src/main/java/dev/yuji/quiz/domain/GameMode.kt" | sort | tr '\n' ' ')
web_modes=$(sed -n "s/^ *{ id: '\([a-z]*\)'.*/\1/p" "$web/src/domain.js" | sort | tr '\n' ' ')
[ "$app_modes" = "$web_modes" ] || problems+=("modes differ: app [$app_modes] web [$web_modes]")

if [ -n "$(git -C "$web" status --porcelain)" ]; then problems+=("web clone has uncommitted changes"); fi
git -C "$web" fetch -q origin 2>/dev/null
[ "$(git -C "$web" rev-parse HEAD)" = "$(git -C "$web" rev-parse origin/main)" ] || problems+=("web commits are not pushed (or origin/main has newer ones)")

if [ ${#problems[@]} -gt 0 ]; then
  echo "WEB VERSION IS BEHIND THE APP:"; printf '  - %s\n' "${problems[@]}"
  echo "update ~/dev/QuizWeb, test, push (see the header of tools/check_web_sync.sh)"; exit 2
fi
echo "web version in sync with the app"

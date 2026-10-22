#!/usr/bin/env bash
# Publishes the question pool (no new APK) to the feed repo's content/ folder (ADR-0012). The apps download it the next
# time they come to the foreground: the beta app from branch bleeding-edge, the stable app from main.
#
#   tools/publish_content.sh beta     check the committed pool and publish it to bleeding-edge
#   tools/publish_content.sh promote  copy exactly the pool on bleeding-edge to main (what users get), then put the same
#                                     files on the web version (its main branch) and push it
#
# Workflow for new questions: edit tools/data/*.tsv (or assets/i18n/*.json), run tools/build_vocab.py (it validates the data
# and raises the pool version in app/src/main/assets/content.json), commit, then `publish_content.sh beta`, try the questions
# in the beta app, and promote when Giovanni says so (nothing reaches the player without the beta app first, ADR-0005).
# A new mode or a new field in the files is not a pool change: it needs an app release (tools/publish_apk.sh).
#
# The web step works in a temporary worktree of the web repo's origin/main, so a web port waiting on a web-<version>
# branch is not disturbed; when that branch is merged later, sw.js VERSION may conflict: keep the higher number + 1.
set -euo pipefail
mode="${1:-}"
[ "$mode" = beta ] || [ "$mode" = promote ] || { sed -n '2,15p' "$0"; exit 1; }

source "$HOME/dev/env.sh"
root="$(cd "$(dirname "$0")/.." && pwd)"
feed="${FEED_DIR:-$HOME/dev/german-quiz-releases}"
web="${WEB_DIR:-$HOME/dev/QuizWeb}"
files=(vocab/nouns.json vocab/prepositions.json vocab/verbs.json vocab/errors.json i18n/de.json i18n/pt.json i18n/es.json)
cd "$root"

git -C "$feed" fetch -q origin
pool_version() { git -C "$feed" show "origin/$1:content/content.json" 2>/dev/null \
  | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["contentVersion"])' 2>/dev/null || echo 0; }
switch_feed() {
  git -C "$feed" checkout -q "$1" 2>/dev/null || git -C "$feed" checkout -q -b "$1" "origin/$1"
  git -C "$feed" reset -q --hard "origin/$1"
}
beta_version=$(pool_version bleeding-edge)
main_version=$(pool_version main)

if [ "$mode" = beta ]; then
  [ -z "$(git status --porcelain)" ] || { echo "commit your changes first (the feed records the commit)"; exit 1; }
  python3 tools/content_manifest.py --check
  version=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["contentVersion"])' app/src/main/assets/content.json)
  [ "$version" -gt "$beta_version" ] || { echo "pool version $version is already on bleeding-edge (has $beta_version): run tools/build_vocab.py after changing the data"; exit 1; }
  commit=$(git rev-parse HEAD)
  # The same parsers and checks the app runs on a download (ContentPackTest, ContentTest), on exactly these files.
  ./gradlew :core:test -q
  switch_feed bleeding-edge
  rm -rf "$feed/content" && mkdir -p "$feed/content/vocab" "$feed/content/i18n"
  for f in "${files[@]}"; do cp "app/src/main/assets/$f" "$feed/content/$f"; done
  python3 -I -c 'import json,sys; m=json.load(open(sys.argv[1])); m["commit"]=sys.argv[2]; open(sys.argv[3],"w").write(json.dumps(m, indent=1)+"\n")' \
    app/src/main/assets/content.json "$commit" "$feed/content/content.json"
  git -C "$feed" add -A content
  git -C "$feed" commit -q -m "beta question pool $version (${commit:0:7})"
  git -C "$feed" push -q origin bleeding-edge
  echo "beta: question pool $version published to bleeding-edge (the beta app picks it up when it is next opened)"
  exit 0
fi

# promote: what was tested in the beta app is what ships.
[ "$beta_version" -gt 0 ] || { echo "no question pool on bleeding-edge: publish it with 'beta' first"; exit 1; }
[ "$beta_version" -gt "$main_version" ] || { echo "main already has question pool $main_version (bleeding-edge: $beta_version)"; exit 1; }
switch_feed main
git -C "$feed" rm -rq --ignore-unmatch content
git -C "$feed" checkout origin/bleeding-edge -- content
git -C "$feed" commit -q -m "promote question pool $beta_version"
git -C "$feed" push -q origin main
echo "promote: question pool $beta_version published to main (the stable app picks it up when it is next opened)"

# Web version: same files on its main branch, cache version bumped so installed copies reload them.
git -C "$web" fetch -q origin
tree="$(mktemp -d)/web"
git -C "$web" worktree add -q --detach "$tree" origin/main
trap 'git -C "$web" worktree remove --force "$tree"' EXIT
for f in "${files[@]}"; do git -C "$feed" show "origin/bleeding-edge:content/$f" > "$tree/$f"; done
if git -C "$tree" diff --quiet; then echo "web version already has these questions"; exit 0; fi
sed -i -E "s/^const VERSION = 'v([0-9]+)';/echo \"const VERSION = 'v\$((\1 + 1))';\"/e" "$tree/sw.js"
(cd "$tree" && npm test --silent)
git -C "$tree" commit -qam "Question pool $beta_version (from the app's feed)"
git -C "$tree" push -q origin HEAD:main
echo "web: question pool $beta_version pushed; https://yujideluca.github.io/german-quiz-web/ serves it within a minute"

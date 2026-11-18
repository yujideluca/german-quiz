#!/usr/bin/env bash
# Push the current commit to main the only way GitHub accepts it (since 2026-11-18).
#
# GitHub protects main of this repository for everyone, the owner included: main may only point to a commit whose
# CI job "gate" has passed. A plain `git push` of a new commit is therefore refused, because CI has not seen it yet.
# This script sends the commit to a short-lived branch ci-<commit>, waits for its CI run, moves main to the commit
# only when the run is green, and deletes the branch either way. Nothing reaches main when CI is red.
#
#   tools/push_main.sh              push HEAD to main
#   tools/push_main.sh --to NAME    the same against another protected branch (used to simulate the rule)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
workflow=quality-gate
target=main
[ "${1:-}" = "--to" ] && { target="${2:?--to needs a branch name}"; shift 2; }
[ $# -eq 0 ] || { echo "usage: tools/push_main.sh [--to BRANCH]"; exit 2; }

sha=$(git rev-parse HEAD)
branch="ci-${sha:0:10}"
git fetch -q origin "$target"
remote=$(git rev-parse FETCH_HEAD)
[ "$sha" != "$remote" ] || { echo "$target on GitHub already has this commit (${sha:0:7}); nothing to push"; exit 0; }
git merge-base --is-ancestor "$remote" "$sha" \
  || { echo "$target on GitHub has commits this copy lacks: git pull --rebase origin $target, then run this again"; exit 1; }

conclusion() {  # of the newest finished CI run for this commit: success, failure, ... or empty
  gh run list --commit "$sha" --workflow "$workflow" --json status,conclusion \
    -q '[.[]|select(.status=="completed")][0].conclusion // ""'
}
drop_branch() { git push -q --no-verify origin --delete "$branch" 2>/dev/null || true; }  # deleting sends no code

if [ "$(conclusion)" != success ]; then
  git push -q origin "$sha:refs/heads/$branch"
  echo "waiting for CI on branch $branch (commit ${sha:0:7})"
  run=""
  for _ in $(seq 60); do  # the run appears a few seconds after the push
    run=$(gh run list --commit "$sha" --workflow "$workflow" --json databaseId,status \
            -q '[.[]|select(.status!="completed")][0].databaseId // ""')
    [ -n "$run" ] && break
    [ "$(conclusion)" = success ] && break
    sleep 5
  done
  if [ -n "$run" ]; then
    gh run watch "$run" --exit-status >/dev/null 2>&1 || true
  fi
  if [ "$(conclusion)" != success ]; then
    drop_branch
    echo "CI did not pass for ${sha:0:7}; $target is unchanged. Read the run: gh run view ${run:-} --log-failed"
    exit 1
  fi
fi

# A plain push: refused, changing nothing, if someone moved the branch meanwhile.
git push -q origin "$sha:refs/heads/$target" || { drop_branch; echo "push to $target refused; $target is unchanged"; exit 1; }
drop_branch
echo "$target is now at ${sha:0:7} (CI passed first)"

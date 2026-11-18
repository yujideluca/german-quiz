#!/usr/bin/env python3
"""Pre-push check of the public feed repository (yujideluca/german-quiz-releases), from which installed apps
fetch their update and their question pool. The publish scripts run the gate before they push; this check is
for a push made any other way, so that a broken or older state never reaches users.

For every branch being pushed it reads the pushed commit (never the working tree) and refuses when:
  - a file outside latest.json, german-quiz.apk, README.md and content/ is in it;
  - latest.json is not valid, or its sha256 is not that of the APK in the same commit;
  - content/content.json lists a file that is missing or whose sha256 differs;
  - versionCode or contentVersion is lower than on the remote branch (an app never takes a lower one).

Installed once per clone of the feed as .git/hooks/pre-push (the feed's branches hold release files only):
    exec python3 -I ~/dev/QuizApp/tools/check_feed.py --pre-push
By hand, in the feed folder:  python3 -I ~/dev/QuizApp/tools/check_feed.py COMMIT [REMOTE_COMMIT]
"""
import hashlib
import json
import subprocess
import sys

ALLOWED = ("latest.json", "german-quiz.apk", "README.md")
ZERO = "0" * 40


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, check=False)


def blob(commit, path):
    done = git("cat-file", "blob", f"{commit}:{path}")
    return done.stdout if done.returncode == 0 else None


def manifest(commit, path, problems=None):
    raw = blob(commit, path)
    if raw is None:
        return None
    try:
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise ValueError("not an object")
        return data
    except ValueError as e:
        if problems is not None:
            problems.append(f"{path} is not valid JSON ({e})")
        return None


def check(commit, remote=None):
    """The reasons why this commit must not be pushed; empty when it may."""
    listed = git("ls-tree", "-r", "--name-only", commit)
    if listed.returncode != 0:
        return [f"cannot read commit {commit}"]
    problems = []
    for path in listed.stdout.decode().splitlines():
        if path not in ALLOWED and not path.startswith("content/"):
            problems.append(f"{path} does not belong in the feed")
    latest = manifest(commit, "latest.json", problems)
    if latest is not None:
        apk = blob(commit, str(latest.get("apk", "")))
        if not isinstance(latest.get("versionCode"), int):
            problems.append("latest.json has no whole-number versionCode")
        if apk is None:
            problems.append(f"latest.json names an APK that is not in the commit: {latest.get('apk')!r}")
        elif hashlib.sha256(apk).hexdigest() != latest.get("sha256"):
            problems.append("latest.json sha256 is not that of the APK in the same commit")
    pool = manifest(commit, "content/content.json", problems)
    if pool is not None:
        files = pool.get("files")
        for name, digest in (files.items() if isinstance(files, dict) else []):
            data = blob(commit, f"content/{name}")
            if data is None:
                problems.append(f"content.json lists content/{name}, which is not in the commit")
            elif hashlib.sha256(data).hexdigest() != digest:
                problems.append(f"content/{name} does not have the sha256 that content.json states")
    if remote and remote != ZERO and git("cat-file", "-e", remote).returncode == 0:
        for path, key, new in (("latest.json", "versionCode", latest), ("content/content.json", "contentVersion", pool)):
            old = manifest(remote, path)
            before, after = (old or {}).get(key), (new or {}).get(key)
            if isinstance(before, int) and (not isinstance(after, int) or after < before):
                problems.append(f"{key} would go from {before} to {after}: an installed app never takes a lower one")
    return problems


def main(argv, stdin):
    if argv[:1] == ["--pre-push"]:
        pushes = [line.split() for line in stdin.read().splitlines() if line.strip()]
        pushes = [(p[0], p[1], p[3]) for p in pushes if len(p) == 4 and p[1] != ZERO]   # a deleted branch has nothing to check
    elif argv:
        pushes = [(argv[0], argv[0], argv[1] if len(argv) > 1 else None)]
    else:
        print(__doc__)
        return 2
    failed = False
    for name, commit, remote in pushes:
        for problem in check(commit, remote):
            failed = True
            print(f"feed check: {name}: {problem}", file=sys.stderr)
    if failed:
        print("feed check: not pushed. Publish with tools/publish_apk.sh or tools/publish_content.sh in QuizApp.", file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], sys.stdin))

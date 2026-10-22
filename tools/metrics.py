#!/usr/bin/env python3
"""Delivery and maintenance metrics for the monthly architecture review (docs/MAINTENANCE.md).

A light version of the DORA metrics, from data that already exists (no tracking to set up):
  * deployment frequency  beta and stable releases per week (feed repo history)
  * lead time             source commit -> stable release, and time a version spent in beta before promote
  * rework rate           share of decision-log rows of Type "Corrective" (DORA's newest metric: work that
                          repairs what was delivered instead of adding value)
  * maintenance balance   decision-log rows per ISO/IEC/IEEE 14764 type
  * size                  Kotlin lines per module and number of @Test functions; largest file

usage: tools/metrics.py [--days N]    (default: the last 30 days)
"""
import datetime as dt, os, re, subprocess, sys
from collections import Counter

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
feed = os.environ.get("QUIZ_FEED", os.path.expanduser("~/dev/german-quiz-releases"))
days = int(sys.argv[sys.argv.index("--days") + 1]) if "--days" in sys.argv else 30
now = dt.datetime.now(dt.timezone.utc)
since = now - dt.timedelta(days=days)

def git(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True).stdout

def hours(delta):
    return f"{delta.total_seconds() / 3600:.1f} h"

print(f"== metrics for the last {days} days (since {since.date()})\n")

# Releases from the feed: "beta 2.6 (versionCode 17, 697b79f)" / "promote 2.5 (versionCode 16, 37c5655)".
releases = []
if os.path.isdir(feed):
    git(feed, "fetch", "-q", "origin")
    for line in git(feed, "log", "--all", "--format=%aI %s").splitlines():
        m = re.match(r"(\S+) (beta|promote) (\S+) \(versionCode (\d+), (\w+)\)", line)
        if m:
            when = dt.datetime.fromisoformat(m[1])
            if when >= since:
                releases.append((when, m[2], m[3], m[5]))
releases.sort()
weeks = max(days / 7, 1)
betas = [r for r in releases if r[1] == "beta"]
promotes = [r for r in releases if r[1] == "promote"]
print("deployment frequency")
print(f"  beta releases:   {len(betas)}  ({len(betas) / weeks:.1f} per week)")
print(f"  stable releases: {len(promotes)}  ({len(promotes) / weeks:.1f} per week)")

print("lead time (stable releases)")
for when, _, version, sha in promotes:
    commit_iso = git(root, "show", "-s", "--format=%aI", sha).strip()
    beta = next((b for b in betas if b[2] == version), None)
    parts = []
    if commit_iso:
        parts.append(f"commit -> stable {hours(when - dt.datetime.fromisoformat(commit_iso))}")
    if beta:
        parts.append(f"in beta {hours(when - beta[0])}")
    print(f"  {version}: " + ", ".join(parts or ["source commit not found"]))
if not promotes:
    print("  no stable release in the period")

# Decision log rows: | 2026-10-22 | Preventive | ... |
design = open(os.path.join(root, "DESIGN.md"), encoding="utf-8").read()
rows = re.findall(r"^\| (\d{4}-\d\d-\d\d) \| (\w+) \|", design, re.M)
recent = Counter(t for d, t in rows if dt.date.fromisoformat(d) >= since.date())
print("maintenance balance (decision-log rows by ISO/IEC/IEEE 14764 type)")
for t in ("Corrective", "Adaptive", "Perfective", "Preventive"):
    print(f"  {t:<11} {recent.get(t, 0)}")
counted = sum(recent.get(t, 0) for t in ("Corrective", "Adaptive", "Perfective", "Preventive"))
if counted:
    print(f"rework rate: {100 * recent.get('Corrective', 0) / counted:.0f} % of the period's changes repaired earlier work")
    print(f"preventive share: {100 * recent.get('Preventive', 0) / counted:.0f} % (target: about 20 %, docs/MAINTENANCE.md)")
print("time to restore: not measured; an incident is recorded as a Corrective row whose text says how long users were affected")

print("size")
for module in ("core", "app"):
    main = test = tests = 0
    for kind in ("main", "test", "androidTest"):
        for d, _, files in os.walk(os.path.join(root, module, "src", kind)):
            for f in files:
                if f.endswith(".kt"):
                    text = open(os.path.join(d, f), encoding="utf-8").read()
                    n = text.count("\n")
                    if kind == "main":
                        main += n
                    else:
                        test += n
                        tests += len(re.findall(r"@Test\b", text))
    print(f"  :{module:<5} {main:>5} lines of code, {test:>5} lines of tests, {tests} @Test functions")

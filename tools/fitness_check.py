#!/usr/bin/env python3
"""Fitness functions: automatic checks that the architecture has not drifted (docs/adr/0009).

  1. The core module stays pure Kotlin: no Android plugin or dependency in core/build.gradle.kts.
     (Its source already cannot import android.*: there is no Android on its classpath.)
  2. No Kotlin source file grows past MAX_FILE_LINES lines; a file that big has several jobs and should be split.
  3. With --apk FILE: the release APK stays below MAX_APK_BYTES.

Raise a limit only on purpose, in the same commit as an ADR or decision-log row that says why.
usage: tools/fitness_check.py [--apk path/to/app.apk]    exit 0 = all limits hold
"""
import os, re, sys

MAX_FILE_LINES = 400
MAX_APK_BYTES = 3_000_000

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
fail = []

core_build = open(os.path.join(root, "core/build.gradle.kts"), encoding="utf-8").read()
code_lines = [l for l in core_build.splitlines() if not l.lstrip().startswith("//")]
if any(re.search(r"android", l, re.I) for l in code_lines):
    fail.append("core/build.gradle.kts mentions Android: the core module must stay pure Kotlin/JVM")

biggest = (0, "")
for module in ("core", "app"):
    for d, _, files in os.walk(os.path.join(root, module, "src")):
        for f in files:
            if f.endswith(".kt"):
                path = os.path.join(d, f)
                n = sum(1 for _ in open(path, encoding="utf-8"))
                biggest = max(biggest, (n, os.path.relpath(path, root)))
                if n > MAX_FILE_LINES:
                    fail.append(f"{os.path.relpath(path, root)}: {n} lines (> {MAX_FILE_LINES})")
print(f"largest Kotlin file: {biggest[1]} ({biggest[0]} lines, limit {MAX_FILE_LINES})")

if "--apk" in sys.argv:
    apk = sys.argv[sys.argv.index("--apk") + 1]
    size = os.path.getsize(apk)
    print(f"release APK: {size / 1e6:.2f} MB (limit {MAX_APK_BYTES / 1e6:.1f} MB)")
    if size > MAX_APK_BYTES:
        fail.append(f"{apk}: {size} bytes (> {MAX_APK_BYTES})")

for f in fail:
    print("FAIL", f)
sys.exit(1 if fail else 0)

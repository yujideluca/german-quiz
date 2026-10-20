#!/usr/bin/env python3
"""Coverage gate: reads the JaCoCo report of the unit tests (./gradlew createStableDebugUnitTestCoverageReport).

Logic packages (domain/, content/ and StudyController) must reach 90 % line coverage in total, and no file in them
may be below 80 %. domain/Clock.kt is excluded: it only wraps System.currentTimeMillis().
UI, Android adapters and debug helpers are covered on a device, not here (see docs/TESTING.md, known gaps).
"""
import os, sys
import xml.etree.ElementTree as ET

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
report = os.path.join(root, "app/build/reports/coverage/test/stable/debug/report.xml")
if not os.path.exists(report):
    sys.exit(f"no coverage report at {report}; run ./gradlew createStableDebugUnitTestCoverageReport")
TOTAL_MIN, FILE_MIN = 90.0, 80.0
EXCLUDE = {"domain/Clock.kt"}
covered = missed = 0
bad = []
for pk in ET.parse(report).getroot().findall("package"):
    pkg = pk.get("name").rsplit("/", 1)[-1]
    for sf in pk.findall("sourcefile"):
        name = f"{pkg}/{sf.get('name')}"
        if not (pkg in ("domain", "content") or name.endswith("quiz/StudyController.kt") or sf.get("name") == "StudyController.kt"):
            continue
        if name in EXCLUDE:
            continue
        c = next((x for x in sf.findall("counter") if x.get("type") == "LINE"), None)
        if c is None:
            continue
        m, v = int(c.get("missed")), int(c.get("covered"))
        covered += v; missed += m
        pct = 100.0 * v / (m + v) if m + v else 100.0
        if pct < FILE_MIN:
            bad.append(f"{name}: {pct:.0f} % (< {FILE_MIN:.0f} %)")
total = 100.0 * covered / (covered + missed)
print(f"logic line coverage: {total:.1f} % ({covered}/{covered + missed})")
if total < TOTAL_MIN:
    bad.append(f"total {total:.1f} % < {TOTAL_MIN:.0f} %")
for b in bad:
    print("FAIL", b)
sys.exit(1 if bad else 0)

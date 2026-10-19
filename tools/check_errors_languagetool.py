#!/usr/bin/env python3
"""Grammar-check the "Fehler finden" sentences with LanguageTool (needs a local LanguageTool install and Java).

Usage: source ~/dev/env.sh && check_errors_languagetool.py /path/to/languagetool-commandline.jar   (needs Java 17+)

Run tools/build_vocab.py first. Two checks, both must hold:
  1. every CORRECTED sentence (wrong chunk replaced by its fix) is clean at the PICKY level, so no sentence teaches
     German that LanguageTool rejects;
  2. no warning falls on a chunk other than the marked one, so the player never meets a second, unintended error.
LanguageTool does not catch every planted error (it found about a quarter of them), so it cannot prove the wrong chunk is
wrong; that part is reviewed by hand. Exit code 1 on any problem.
"""
import json, subprocess, sys, tempfile
from pathlib import Path

ASSET = Path(__file__).resolve().parent.parent / "app/src/main/assets/vocab/errors.json"
STYLE_ONLY = {"GERMAN_WORD_REPEAT_BEGINNING_RULE", "DE_REPEATEDWORDS_LEIDER", "WORTWIEDERHOLUNG_SEHR"}  # repeats across neighbouring test lines


# A warning that is only a side effect of the planted error (reviewed by hand): LanguageTool reads the wrong "das Struktur"
# in F50 as a relative pronoun and asks for a comma later in the sentence.
KNOWN_SIDE_EFFECTS = {("F50", "COMMA_BEHIND_RELATIVE_CLAUSE")}


def run(jar, lines):
    with tempfile.NamedTemporaryFile("w", suffix=".txt", encoding="utf-8", delete=False) as f:
        f.write("\n".join(lines) + "\n")
    out = subprocess.run(["java", "-jar", jar, "-l", "de-DE", "--level", "PICKY", "--json", f.name],
                         capture_output=True, text=True).stdout
    return json.loads(out)["matches"]


def main(jar):
    entries = json.loads(ASSET.read_text(encoding="utf-8"))
    problems = []
    corrected = [" ".join(e["fix"] if i == e["errorIndex"] else s for i, s in enumerate(e["segments"])) for e in entries]
    text = "\n".join(corrected) + "\n"
    for m in run(jar, corrected):
        if m["rule"]["id"] in STYLE_ONLY:
            continue
        o = m["offset"]
        problems.append(f'{entries[text.count(chr(10), 0, o)]["id"]} corrected sentence: {m["rule"]["id"]} {text[o:o + m["length"]]!r}')
    wrong = [" ".join(e["segments"]) for e in entries]
    text = "\n".join(wrong) + "\n"
    starts, pos = [], 0
    for line in wrong:
        starts.append(pos); pos += len(line) + 1
    for m in run(jar, wrong):
        if m["rule"]["id"] in STYLE_ONLY:
            continue
        o, end = m["offset"], m["offset"] + m["length"]
        i = max(k for k, s in enumerate(starts) if s <= o)
        e, p, spans = entries[i], starts[i], []
        for s in e["segments"]:
            spans.append((p, p + len(s))); p += len(s) + 1
        ws, we = spans[e["errorIndex"]]
        if not (o < we and end > ws) and (e["id"], m["rule"]["id"]) not in KNOWN_SIDE_EFFECTS:
            problems.append(f'{e["id"]} warning outside the marked chunk: {m["rule"]["id"]} {text[o:end]!r}')
    print("\n".join(problems) if problems else f"ok: {len(entries)} sentences")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main(sys.argv[1])

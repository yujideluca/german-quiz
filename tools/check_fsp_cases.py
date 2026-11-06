#!/usr/bin/env python3
"""Validate FSP case simulations (tools/data/fsp/<id>.json) against the one fixed template (ADR-0014).

Every case walks through the same 34 steps of the Baden-Wuerttemberg Fachsprachpruefung: part 1 Arzt-Patienten-Gespraech
(16 steps), part 2 Dokumentation (7), part 3 Arzt-Arzt-Gespraech (6) plus the Fachbegriffe list (5). Each step offers 4
answers, exactly one right, with a German explanation for every answer.

usage:
  check_fsp_cases.py              check every case file and the case list
  check_fsp_cases.py FILE...      check only these files (for authors)
Exit 1 with one line per problem.
"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CASE_DIR = ROOT / "tools/data/fsp"
CASE_LIST = ROOT / "tools/data/fsp_cases.tsv"

TEMPLATE = [
    (1, "Begrüßung"), (1, "Personalien"), (1, "Hauptbeschwerde"), (1, "Leitsymptom genauer"),
    (1, "Beginn und Verlauf"), (1, "Charakter und Stärke"), (1, "Begleitsymptome"), (1, "Vegetative Anamnese"),
    (1, "Vorerkrankungen"), (1, "Medikamente"), (1, "Allergien"), (1, "Noxen"), (1, "Familienanamnese"),
    (1, "Sozialanamnese"), (1, "Verdachtsdiagnose erklären"), (1, "Weiteres Vorgehen"),
    (2, "Einleitung"), (2, "Aktuelle Anamnese"), (2, "Begleitsymptome und vegetative Anamnese"),
    (2, "Vorerkrankungen und Medikation"), (2, "Allergien, Noxen und Sozialanamnese"),
    (2, "Verdachtsdiagnose und Differenzialdiagnosen"), (2, "Procedere"),
    (3, "Übergabe beginnen"), (3, "Befunde"), (3, "Vorgeschichte"), (3, "Verdachtsdiagnose"), (3, "Procedere"),
    (3, "Rückfrage"),
] + [(3, "Fachbegriffe")] * 5
KEYS = ["id", "area", "title", "diagnosis", "source", "bwReports", "patient", "steps"]
STEP_KEYS = ["part", "section", "situation", "question", "options", "answer", "feedback", "reply"]
SOURCES = {"reported_BW", "common_FSP"}
MAX_OPTION = 420


def case_list():
    """id -> (area, diagnosis, leitsymptom, source, bwReports), in list order."""
    out = {}
    for line in CASE_LIST.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            c = line.split("|")
            out[c[0]] = (c[1], c[2], c[3], c[4], int(c[5]))
    return out


def area_order():
    seen = []
    for area, *_ in case_list().values():
        if area not in seen:
            seen.append(area)
    return seen


def check_case(path, listed):
    errs = []
    where = path.name
    try:
        case = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as e:
        return [f"{where}: not valid JSON: {e}"]
    if list(case) != KEYS:
        return [f"{where}: keys must be exactly {KEYS} in this order, got {list(case)}"]
    cid = case["id"]
    if path.stem != cid:
        errs.append(f"{where}: id '{cid}' must equal the file name")
    if cid not in listed:
        errs.append(f"{where}: id '{cid}' is not in tools/data/fsp_cases.tsv")
    else:
        area, _, _, source, bw = listed[cid]
        if case["area"] != area:
            errs.append(f"{where}: area must be '{area}' (case list)")
        if case["source"] != source or case["bwReports"] != bw:
            errs.append(f"{where}: source/bwReports must be {source}/{bw} (case list)")
    if case["source"] not in SOURCES:
        errs.append(f"{where}: source must be one of {SOURCES}")
    for k in ("title", "diagnosis", "patient"):
        if not isinstance(case[k], str) or not case[k].strip():
            errs.append(f"{where}: {k} must be a non-empty string")
    # The title is shown before the case starts: it names the complaint, never the diagnosis.
    for word in re.findall(r"[A-Za-zÄÖÜäöüß]{6,}", case["diagnosis"]):
        if word.lower() in case["title"].lower() and word.lower() not in {"akutes", "akuter", "akute", "chronische"}:
            errs.append(f"{where}: title gives away the diagnosis ('{word}')")
    steps = case["steps"]
    if not isinstance(steps, list) or len(steps) != len(TEMPLATE):
        return errs + [f"{where}: needs exactly {len(TEMPLATE)} steps, got {len(steps) if isinstance(steps, list) else steps}"]
    longest_right = 0
    for i, (step, (part, section)) in enumerate(zip(steps, TEMPLATE), 1):
        w = f"{where} step {i} ({section})"
        if list(step) != STEP_KEYS:
            errs.append(f"{w}: keys must be exactly {STEP_KEYS} in this order"); continue
        if step["part"] != part or step["section"] != section:
            errs.append(f"{w}: must be part {part}, section '{section}', got {step['part']}/'{step['section']}'")
        for k in ("situation", "reply"):
            if step[k] is not None and (not isinstance(step[k], str) or not step[k].strip()):
                errs.append(f"{w}: {k} must be null or a non-empty string")
        if not isinstance(step["question"], str) or not step["question"].strip():
            errs.append(f"{w}: empty question")
        opts, fb, ans = step["options"], step["feedback"], step["answer"]
        if not (isinstance(opts, list) and len(opts) == 4 and all(isinstance(o, str) and o.strip() for o in opts)):
            errs.append(f"{w}: needs 4 non-empty options"); continue
        if len({o.strip() for o in opts}) != 4:
            errs.append(f"{w}: duplicate options")
        if any(len(o) > MAX_OPTION for o in opts):
            errs.append(f"{w}: an option is longer than {MAX_OPTION} characters")
        if not isinstance(ans, int) or isinstance(ans, bool) or not 0 <= ans <= 3:
            errs.append(f"{w}: answer must be 0..3"); continue
        if not (isinstance(fb, list) and len(fb) == 4 and all(isinstance(x, str) and x.strip() for x in fb)):
            errs.append(f"{w}: needs 4 non-empty feedback texts"); continue
        if not fb[ans].startswith("Richtig"):
            errs.append(f"{w}: feedback of the right answer must start with 'Richtig'")
        for j, x in enumerate(fb):
            if j != ans and x.startswith("Richtig"):
                errs.append(f"{w}: feedback {j} starts with 'Richtig' but is not the answer")
        if section == "Fachbegriffe":
            if not re.fullmatch(r"Was bedeutet „[^“”]+“\?", step["question"]):
                errs.append(f"{w}: Fachbegriffe question must read: Was bedeutet „<Begriff>“?")
        elif max(range(4), key=lambda j: len(opts[j])) == ans:
            longest_right += 1
    scored = len(TEMPLATE) - 5
    # A player must not be able to pass by always tapping the longest answer.
    if longest_right > scored * 0.6:
        errs.append(f"{where}: the right answer is the longest option in {longest_right}/{scored} steps (max 60%): "
                    "make wrong options as complete as right ones (wrong content, wrong register, wrong grammar)")
    return errs


def main(argv):
    listed = case_list()
    files = [Path(a) for a in argv] if argv else sorted(CASE_DIR.glob("*.json"))
    errs = []
    for f in files:
        errs += check_case(f, listed)
    if not argv:
        missing = sorted(set(listed) - {f.stem for f in files})
        if missing:
            errs.append(f"cases in the list without a file: {', '.join(missing)}")
    for e in errs:
        print(e)
    print(f"{len(files)} case file(s) checked, {len(errs)} problem(s)")
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

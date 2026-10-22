#!/usr/bin/env python3
"""Build the app's vocabulary assets from tools/data/*.tsv and validate them.

Usage:
  build_vocab.py                  validate + write app/src/main/assets/vocab/*.json and refresh
                                  assets/content.json (pool version, see tools/content_manifest.py)
  build_vocab.py --verify-nouns   additionally cross-check gender and plural of every
                                  noun against de.wiktionary.org (needs network)

Fails (exit 1) on any structural problem so bad data never reaches the app.
"""
import argparse, json, re, subprocess, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "tools/data"
OUT = ROOT / "app/src/main/assets/vocab"
ARTICLES = {"der", "die", "das"}
KINDS = {"V", "A", "N", "E"}
CASES = {"A", "D"}
REGISTERS = {"F": "Forumsbeitrag", "M": "Formelle E-Mail"}
ERROR_TYPES = {
    "PASSIV": "Passive: werden, not sein",
    "VERBSTELLUNG": "Word order: verb position",
    "DEKLINATION": "Article / adjective ending",
    "PRAEPOSITION": "Preposition and case",
    "REKTION": "Verb or noun + preposition / case",
    "ANREDE": "Sie vs. du (formal register)",
    "SCHREIBUNG": "Capitalisation and spelling",
    "PLURAL": "Plural and gender",
    "WORTWAHL": "Word choice / collocation",
    "KONNEKTOR": "Connector",
    "INFINITIV": "Infinitive with zu / um … zu",
    "KONJUGATION": "Verb form and agreement",
}


def rows(path):
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if line and not line.startswith("#"):
            yield n, line.split("|")


def load_nouns():
    out, errors, seen = [], [], set()
    for n, c in rows(DATA / "nouns.tsv"):
        if len(c) not in (3, 4) or c[0] not in ARTICLES or not c[1] or not c[2]:
            errors.append(f"nouns.tsv:{n} malformed: {c}"); continue
        if c[1] in seen:
            errors.append(f"nouns.tsv:{n} duplicate noun {c[1]}"); continue
        seen.add(c[1])
        alts = [a.strip() for a in c[3].split(",")] if len(c) == 4 and c[3] else []
        out.append({"article": c[0], "singular": c[1], "plural": c[2], "alsoValidPlurals": alts})
    return out, errors


def load_prepositions():
    out, errors, seen = [], [], set()
    for n, c in rows(DATA / "prepositions.tsv"):
        if len(c) not in (5, 6, 7) or c[0] not in KINDS or c[3] not in CASES:
            errors.append(f"prepositions.tsv:{n} malformed: {c}"); continue
        if c[4].count("___") != 1:
            errors.append(f"prepositions.tsv:{n} sentence needs exactly one ___: {c[4]}"); continue
        key = (c[1], c[2])  # card id = headword + preposition, must be unique
        if key in seen:
            errors.append(f"prepositions.tsv:{n} duplicate {key}"); continue
        seen.add(key)
        alts = [a.strip() for a in c[5].split(",")] if len(c) >= 6 and c[5] else []
        entry = {"kind": c[0], "headword": c[1], "preposition": c[2], "case": c[3],
                 "sentence": c[4], "alsoValidPrepositions": alts}
        if len(c) == 7:
            if not c[6].strip():
                errors.append(f"prepositions.tsv:{n} empty hint column"); continue
            entry["hint"] = c[6].strip()
        out.append(entry)
    # The same headword with different prepositions is ambiguous without a hint saying which meaning is asked.
    by_headword = {}
    for e in out:
        by_headword.setdefault((e["kind"], e["headword"]), []).append(e)
    for (kind, headword), group in by_headword.items():
        if len({e["preposition"] for e in group}) > 1:
            for e in group:
                if "hint" not in e:
                    errors.append(f"prepositions.tsv: '{headword} {e['preposition']}' needs a hint "
                                  f"(the headword has several prepositions)")
    return out, errors


def load_verbs():
    out, errors, seen = [], [], set()
    for n, c in rows(DATA / "verbs.tsv"):
        if len(c) != 4 or not all(x.strip() for x in c):
            errors.append(f"verbs.tsv:{n} malformed: {c}"); continue
        inf, pret, perfect, pres = (x.strip() for x in c)
        if not re.fullmatch(r"(hat|ist) \S+", perfect):
            errors.append(f"verbs.tsv:{n} perfect must be 'hat/ist <participle>': {perfect}"); continue
        if inf in seen:
            errors.append(f"verbs.tsv:{n} duplicate verb {inf}"); continue
        seen.add(inf)
        out.append({"infinitive": inf, "preterite": pret, "perfect": perfect, "present": pres})
    return out, errors


def load_errors():
    """Sentences with one wrong chunk to tap. Chunks are separated by ' / '; the wrong one is wrapped in {{ }}."""
    out, errors, seen = [], [], set()
    for n, c in rows(DATA / "errors.tsv"):
        where = f"errors.tsv:{n}"
        if len(c) != 7 or not all(x.strip() for x in c):
            errors.append(f"{where} needs 7 non-empty columns, got {len(c)}: {c[:1]}"); continue
        id_, register, kind, sentence, fix, rule, expression = (x.strip() for x in c)
        if id_ in seen:
            errors.append(f"{where} duplicate id {id_}"); continue
        seen.add(id_)
        if register not in REGISTERS:
            errors.append(f"{where} unknown register {register}"); continue
        if kind not in ERROR_TYPES:
            errors.append(f"{where} unknown type {kind}"); continue
        raw = [x.strip() for x in sentence.split(" / ")]
        wrong = [i for i, chunk in enumerate(raw) if "{{" in chunk or "}}" in chunk]
        if len(wrong) != 1 or not re.fullmatch(r"\{\{[^{}]+\}\}", raw[wrong[0]]):
            errors.append(f"{where} exactly one whole chunk must be wrapped in {{{{ }}}}: {sentence}"); continue
        chunks = [chunk[2:-2].strip() if i == wrong[0] else chunk for i, chunk in enumerate(raw)]
        if len(chunks) < 3 or any(not chunk for chunk in chunks):
            errors.append(f"{where} needs at least 3 non-empty chunks"); continue
        if len(set(chunks)) != len(chunks):
            errors.append(f"{where} duplicate chunk text (the player could not tell them apart): {chunks}"); continue
        if fix == chunks[wrong[0]]:
            errors.append(f"{where} fix equals the wrong chunk"); continue
        if fix in chunks:
            errors.append(f"{where} fix text appears as another chunk (answer would be ambiguous): {fix}"); continue
        out.append({"id": id_, "register": REGISTERS[register], "errorType": ERROR_TYPES[kind],
                    "segments": chunks, "errorIndex": wrong[0], "fix": fix, "rule": rule,
                    "expression": expression})
    return out, errors


def _api_get(url):
    """GET JSON, sleeping for Retry-After on HTTP 429 (Wikimedia rate limit)."""
    req = urllib.request.Request(url, headers={"User-Agent": "QuizAppVocabVerifier/1.0 (personal project)"})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(int(e.headers.get("Retry-After", 30)) + 1)
    raise RuntimeError("rate limited too long")


def wiktionary_facts(titles, batch=40):
    """Return {title: (set_of_genders, set_of_plurals)} from de.wiktionary wikitext."""
    facts = {}
    for i in range(0, len(titles), batch):
        chunk = titles[i:i + batch]
        q = urllib.parse.urlencode({"action": "query", "prop": "revisions", "rvprop": "content",
                                    "rvslots": "main", "titles": "|".join(chunk), "format": "json"})
        data = _api_get(f"https://de.wiktionary.org/w/api.php?{q}")["query"]
        for page in data["pages"].values():
            try:
                text = page["revisions"][0]["slots"]["main"]["*"]
            except (KeyError, IndexError):
                continue
            genders = set(re.findall(r"\|Genus(?: \d)?=([mfn])\b", text))
            plurals = {x.strip() for x in re.findall(r"\|Nominativ Plural(?: \d)?=([^\n|]+)", text)}
            facts[page["title"]] = (genders, plurals)
        time.sleep(1)
    return facts


def verify_nouns(nouns):
    gender = {"der": "m", "die": "f", "das": "n"}
    facts = wiktionary_facts([n["singular"] for n in nouns])
    problems, unchecked = [], 0
    for n in nouns:
        res = facts.get(n["singular"])
        if not res or not res[0]:
            unchecked += 1; continue
        genders, plurals = res
        if gender[n["article"]] not in genders:
            problems.append(f"{n['article']} {n['singular']}: Wiktionary gender={sorted(genders)}")
        accepted = {n["plural"], *n["alsoValidPlurals"]}
        if plurals and n["plural"] not in plurals:
            problems.append(f"{n['singular']}: plural '{n['plural']}' vs Wiktionary {sorted(plurals)}")
    return problems, unchecked


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verify-nouns", action="store_true")
    a = ap.parse_args()

    nouns, e1 = load_nouns()
    preps, e2 = load_prepositions()
    verbs, e3 = load_verbs()
    sentences, e4 = load_errors()
    errors = e1 + e2 + e3 + e4
    if errors:
        print("\n".join(errors)); sys.exit(1)

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "nouns.json").write_text(json.dumps(nouns, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "prepositions.json").write_text(json.dumps(preps, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "verbs.json").write_text(json.dumps(verbs, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "errors.json").write_text(json.dumps(sentences, ensure_ascii=False, indent=1), encoding="utf-8")
    kinds = {k: sum(p["kind"] == k for p in preps) for k in sorted(KINDS)}
    subprocess.run([sys.executable, str(ROOT / "tools/content_manifest.py")], check=True)  # new version if anything changed
    print(f"nouns: {len(nouns)} | preposition items: {len(preps)} {kinds} | verbs: {len(verbs)} | error sentences: {len(sentences)} -> {OUT}")

    if a.verify_nouns:
        problems, unchecked = verify_nouns(nouns)
        print(f"wiktionary check: {len(nouns) - unchecked} verified, {unchecked} not parseable, "
              f"{len(problems)} mismatches")
        for p in problems:
            print("  MISMATCH", p)
        sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()

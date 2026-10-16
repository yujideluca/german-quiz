#!/usr/bin/env python3
"""Grounded, verified quiz-question generator for the local Qwen (llama.cpp) server.

Pipeline per topic (every stage is deterministic code except the two model calls):
  1. GROUND   Fetch a Wikipedia extract; the model may only use that passage
              (retrieval-grounded generation cuts hallucination vs. closed-book).
  2. GENERATE JSON-schema constrained decoding (llama.cpp grammar) -> always valid
              structure; model emits correct answer and distractors separately and
              must quote verbatim evidence from the passage.
  3. FILTER   Code checks: evidence really occurs in the passage, evidence supports
              the stated answer, no answer leak, no meta-references, 4 unique
              options, no "all of the above", near-duplicate removal.
  4. VERIFY   Blind re-answering from the passage under two independent random
              option orders (self-consistency + position-bias control). Accepted only
              if both agree with the key.
  5. SHUFFLE  Option order is randomised in code (LLMs over-pick certain positions).

Limits (be honest): the verifier is the same model, so a fact that is wrong *in the
source passage* or misread the same way twice can still pass; the passage grounding
and verbatim-evidence rule are what make that unlikely. Spot-check with --sample.
Wikipedia text is CC BY-SA: the source title/url is stored per question.
"""
import argparse, difflib, hashlib, json, random, re, subprocess, sys, time
import urllib.parse, urllib.request
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets/questions.json"
SERVER = "http://127.0.0.1:8080"
UA = {"User-Agent": "QuizAppQuestionGenerator/1.0 (personal project)"}
MAX_PASSAGE = 5000

GEN_SCHEMA = {
    "type": "object",
    "properties": {"questions": {"type": "array", "items": {
        "type": "object",
        "properties": {
            "question": {"type": "string"},
            "correct": {"type": "string"},
            "distractors": {"type": "array", "items": {"type": "string"},
                            "minItems": 3, "maxItems": 3},
            "evidence": {"type": "string"},
            "explanation": {"type": "string"},
        },
        "required": ["question", "correct", "distractors", "evidence", "explanation"],
    }}},
    "required": ["questions"],
}
VERIFY_SCHEMA = {"type": "object", "properties": {"choice": {"type": "integer", "enum": [0, 1, 2, 3]}},
                 "required": ["choice"]}

GEN_SYSTEM = (
    "You write multiple-choice quiz questions strictly from a given passage.\n"
    "Rules: use only facts stated in the passage; one unambiguous correct answer; "
    "3 plausible but clearly wrong distractors of similar length and type; the question "
    "must be self-contained and answerable by someone who has not read the passage "
    "(never mention or start with 'according to' / 'the passage' / 'the text'); "
    "do not put the answer in the question; 'evidence' is ONE sentence copied word for "
    "word from the passage that proves the answer; 'explanation' is one short sentence."
)
VERIFY_SYSTEM = ("Answer using ONLY the passage. Reply with JSON {\"choice\": N} where N is "
                 "the 0-based index of the correct option.")

# ---------------------------------------------------------------- helpers

def norm(s):
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", s.lower())).strip()

def tokens(s):
    return set(norm(s).split())

def jaccard(a, b):
    a, b = tokens(a), tokens(b)
    return len(a & b) / len(a | b) if a and b else 0.0

def http_json(url, body=None, timeout=600, retries=3):
    for attempt in range(retries):
        try:
            data = json.dumps(body).encode() if body is not None else None
            req = urllib.request.Request(url, data, {**UA, "Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.load(r)
        except Exception:
            if attempt == retries - 1:
                raise
            time.sleep(2 * (attempt + 1))

def fetch_passage(title):
    q = urllib.parse.urlencode({
        "action": "query", "prop": "extracts|info", "inprop": "url", "explaintext": 1,
        "exchars": MAX_PASSAGE, "redirects": 1, "titles": title, "format": "json"})
    pages = http_json(f"https://en.wikipedia.org/w/api.php?{q}", timeout=30)["query"]["pages"]
    page = next(iter(pages.values()))
    text = re.sub(r"\n==+ .*? ==+\n", "\n", page.get("extract", "")).strip()
    return page.get("title", title), page.get("fullurl", ""), text

def chat(system, user, schema, max_tokens, think=False, temperature=0.2, seed=None):
    body = {
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "response_format": {"type": "json_schema", "json_schema": {"name": "out", "schema": schema}},
        "temperature": temperature, "max_tokens": max_tokens,
        "chat_template_kwargs": {"enable_thinking": think},
    }
    if seed is not None:
        body["seed"] = seed
    d = http_json(f"{SERVER}/v1/chat/completions", body)
    return json.loads(d["choices"][0]["message"]["content"]), d.get("usage", {})

# ---------------------------------------------------------------- stages

META = re.compile(r"\b(the (passage|text|article|excerpt)|as (stated|mentioned|described) (above|here|in))\b", re.I)
ALLABOVE = re.compile(r"\b(all|none|both) of the (above|these)\b|\bnone of these\b", re.I)

def evidence_in_passage(ev, passage):
    e, p = norm(ev), norm(passage)
    if len(e) < 15:
        return False
    if e in p:
        return True
    m = difflib.SequenceMatcher(None, p, e, autojunk=False).find_longest_match(0, len(p), 0, len(e))
    return m.size / len(e) >= 0.9

def evidence_supports(correct, ev):
    toks = [t for t in norm(correct).split() if len(t) > 2 or t.isdigit()]
    if not toks:
        return norm(correct) in norm(ev)
    ev_t = tokens(ev)
    return sum(t in ev_t for t in toks) / len(toks) >= 0.8

def structural_reject(q, passage):
    """Return a short reason string if the candidate must be dropped, else None."""
    opts = [q["correct"].strip()] + [d.strip() for d in q["distractors"]]
    text = q["question"].strip()
    if len(text) < 12 or not text.endswith("?"):
        return "bad_question_form"
    if len({norm(o) for o in opts}) != 4 or any(not o for o in opts):
        return "dup_or_empty_option"
    if any(ALLABOVE.search(o) for o in opts):
        return "all_of_above"
    if META.search(text):
        return "meta_reference"
    if norm(q["correct"]) and norm(q["correct"]) in norm(text) and len(norm(q["correct"])) > 3:
        return "answer_leak"
    if max(map(len, opts)) > 3 * max(8, min(map(len, opts))):
        return "length_imbalance"
    if not evidence_in_passage(q["evidence"], passage):
        return "evidence_not_in_passage"
    if not evidence_supports(q["correct"], q["evidence"]):
        return "evidence_not_supporting"
    return None

def verify(q, passage, rng, think, n=2):
    """Blind re-answer under n independent shuffles. True only if all agree with key."""
    opts = [q["correct"].strip()] + [d.strip() for d in q["distractors"]]
    toks = 0
    for _ in range(n):
        order = opts[:]
        rng.shuffle(order)
        listing = "\n".join(f"{i}. {o}" for i, o in enumerate(order))
        user = f"Passage:\n{passage}\n\nQuestion: {q['question']}\n{listing}"
        out, usage = chat(VERIFY_SYSTEM, user, VERIFY_SCHEMA, 16 if not think else 2048,
                          think=think, temperature=0.0)
        toks += usage.get("total_tokens", 0)
        if order[out["choice"]] != opts[0]:
            return False, toks
    return True, toks

def build_item(q, cat_id, rng, title, url):
    opts = [q["correct"].strip()] + [d.strip() for d in q["distractors"]]
    rng.shuffle(opts)
    return {
        "id": f"{cat_id}-{hashlib.sha1(norm(q['question']).encode()).hexdigest()[:8]}",
        "text": q["question"].strip(),
        "options": opts,
        "answerIndex": opts.index(q["correct"].strip()),
        "explanation": q["explanation"].strip(),
        "source": f"Wikipedia: {title} ({url}) CC BY-SA",
    }

# ---------------------------------------------------------------- server

def ensure_server():
    def up():
        try:
            return http_json(f"{SERVER}/health", timeout=3, retries=1).get("status") == "ok"
        except Exception:
            return False
    if up():
        return None
    proc = subprocess.Popen([str(Path.home() / "models/run-qwen35.sh"), "balanced"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(90):
        if up():
            return proc
        time.sleep(2)
    proc.terminate()
    sys.exit("llama-server did not become ready")

# ---------------------------------------------------------------- main

def load_bank():
    return json.loads(ASSETS.read_text()) if ASSETS.exists() else {"categories": []}

def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--topics", default=str(ROOT / "tools/topics.json"))
    ap.add_argument("--category", action="append", help="only these category ids")
    ap.add_argument("--per-topic", type=int, default=3)
    ap.add_argument("--out", default=str(ROOT / "tools/generated"))
    ap.add_argument("--merge", action="store_true", help="merge accepted into app assets")
    ap.add_argument("--think", action="store_true", help="enable model thinking in verification (slower)")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--sample", type=int, default=0, help="print N accepted questions for spot-check")
    a = ap.parse_args()

    topics = json.loads(Path(a.topics).read_text())
    rng = random.Random(a.seed)
    bank = load_bank()
    known = [q["text"] for c in bank["categories"] for q in c["questions"]]
    out_dir = Path(a.out); out_dir.mkdir(parents=True, exist_ok=True)

    proc = ensure_server()
    stats, rejects, accepted = Counter(), [], {}
    tok_total, t0 = 0, time.time()
    try:
        for cat_id, cat in topics.items():
            if a.category and cat_id not in a.category:
                continue
            for topic in cat["topics"]:
                try:
                    title, url, passage = fetch_passage(topic)
                except Exception as e:
                    stats["fetch_failed"] += 1; continue
                if len(passage) < 400:
                    stats["passage_too_short"] += 1; continue
                user = f"Passage:\n{passage}\n\nWrite {a.per_topic} questions."
                try:
                    gen, usage = chat(GEN_SYSTEM, user, GEN_SCHEMA, 450 * a.per_topic,
                                      think=False, temperature=0.4, seed=rng.randrange(1 << 30))
                except Exception:
                    stats["gen_failed"] += 1; continue
                tok_total += usage.get("total_tokens", 0)
                for q in gen["questions"]:
                    stats["generated"] += 1
                    why = structural_reject(q, passage)
                    if why is None and any(jaccard(q["question"], k) >= 0.7 for k in known):
                        why = "duplicate"
                    if why is None:
                        ok, t = verify(q, passage, rng, a.think)
                        tok_total += t
                        if not ok:
                            why = "verify_disagree"
                    if why:
                        stats[f"rejected:{why}"] += 1
                        rejects.append({"topic": title, "reason": why, "q": q})
                        continue
                    item = build_item(q, cat_id, rng, title, url)
                    accepted.setdefault(cat_id, []).append(item)
                    known.append(item["text"])
                    stats["accepted"] += 1
                print(f"[{cat_id}] {title}: accepted so far {stats['accepted']}/{stats['generated']}", flush=True)
    finally:
        if proc:
            proc.terminate()

    for cat_id, items in accepted.items():
        (out_dir / f"{cat_id}.json").write_text(json.dumps(items, ensure_ascii=False, indent=1))
    (out_dir / "rejected.json").write_text(json.dumps(rejects, ensure_ascii=False, indent=1))

    if a.merge and accepted:
        by_id = {c["id"]: c for c in bank["categories"]}
        for cat_id, items in accepted.items():
            c = by_id.get(cat_id) or {"id": cat_id, "title": topics[cat_id]["title"], "questions": []}
            if cat_id not in by_id:
                bank["categories"].append(c)
            c["questions"].extend(items)
        ASSETS.write_text(json.dumps(bank, ensure_ascii=False, indent=2))

    rate = stats["accepted"] / stats["generated"] if stats["generated"] else 0
    print(f"\nDONE {time.time()-t0:.0f}s, ~{tok_total} tokens | generated {stats['generated']}, "
          f"accepted {stats['accepted']} ({rate:.0%}){' | merged' if a.merge else ''}")
    for k, v in sorted(stats.items()):
        if k.startswith("rejected") or k.endswith("failed") or k.endswith("short"):
            print(f"  {k}: {v}")
    for cat_id, items in accepted.items():
        print(f"  {cat_id}: +{len(items)}  -> {out_dir / (cat_id + '.json')}")
    for item in [i for v in accepted.values() for i in v][: a.sample]:
        print(f"\n  Q: {item['text']}\n  A: {item['options'][item['answerIndex']]}  (src: {item['source'][:60]})")

if __name__ == "__main__":
    main()

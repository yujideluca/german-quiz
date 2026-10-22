#!/usr/bin/env python3
"""The question pool's manifest: app/src/main/assets/content.json (ADR-0012).

It lists the SHA-256 of every pool file (assets/vocab/*.json, assets/i18n/*.json) and a contentVersion that rises
by one whenever any of them changes. The app compares that version with the pack on the update feed
(tools/publish_content.sh) to decide whether to download new questions.

usage:
  content_manifest.py           rewrite content.json; bumps contentVersion only if a file changed (build_vocab.py runs this)
  content_manifest.py --check   exit 1 if content.json does not match the files (quality gate)
"""
import hashlib, json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
MANIFEST = ASSETS / "content.json"
SCHEMA = 1  # = CONTENT_SCHEMA in core/.../content/ContentPack.kt; raise both together when the file format changes
FILES = ["vocab/nouns.json", "vocab/prepositions.json", "vocab/verbs.json", "vocab/errors.json",
         "i18n/de.json", "i18n/pt.json", "i18n/es.json"]  # = CONTENT_FILES


def hashes():
    return {f: hashlib.sha256((ASSETS / f).read_bytes()).hexdigest() for f in FILES}


def current():
    return json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else None


def main():
    old, now = current(), hashes()
    if "--check" in sys.argv[1:]:
        if old is None or old.get("files") != now or old.get("schema") != SCHEMA:
            print("app/src/main/assets/content.json does not match the pool files: run tools/content_manifest.py and commit it")
            sys.exit(1)
        print(f"content manifest ok: version {old['contentVersion']}, {len(now)} files")
        return
    if old is not None and old.get("files") == now and old.get("schema") == SCHEMA:
        print(f"content unchanged: version {old['contentVersion']}")
        return
    version = (old or {}).get("contentVersion", 0) + 1
    manifest = {"schema": SCHEMA, "contentVersion": version, "files": now}
    MANIFEST.write_text(json.dumps(manifest, indent=1) + "\n", encoding="utf-8")
    print(f"content.json: version {version}")


if __name__ == "__main__":
    main()

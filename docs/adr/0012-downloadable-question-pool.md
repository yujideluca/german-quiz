# ADR-0012: The question pool is a versioned pack the app can download from the update feed

- **Status:** accepted
- **Date:** 2026-10-22
- **Type (ISO/IEC/IEEE 14764):** perfective
- **Decided by:** Giovanni (goal and approach: "expand the question pool without updating the app all the time"); design by Claude Code

## Context

The questions (`assets/vocab/*.json`) and the translations of their English text (`assets/i18n/*.json`) were built into
the APK, so every new word meant a new app version, a beta, a promote and a Play Protect tap. The pool grows much more
often than the code changes. Progress is keyed by card ids built from the content (`article:Haus`,
`preposition:warten:auf`, ...), so the pool can change without migrating progress.

## Decision

- The seven pool files form one **pack** with a manifest, `content.json`: `schema` (the file format; `CONTENT_SCHEMA` = 1
  in the app), `contentVersion` (an integer that rises by one whenever any file changes) and the SHA-256 of every file.
  The pack built into the APK lives in `app/src/main/assets/content.json`; `tools/build_vocab.py` refreshes it through
  `tools/content_manifest.py`, and the quality gate fails when it does not match the files.
- The feed repo gets a `content/` folder next to the APK, on both branches. `tools/publish_content.sh beta` puts the
  committed pack on `bleeding-edge`; `promote` copies exactly that pack to `main` and the same files to the web version.
- Each time the app comes to the foreground (with the update check, at most every 30 s), it reads `content/content.json`
  of its own branch. It downloads a pack only when the schema is the one it reads and the version is higher than the
  pack in use. It installs the pack only when every file is present, matches its checksum, parses, and every mode keeps
  a question (`validateContentPack`, the same check the gate runs on the built-in pack). Installing writes to
  `files/content/staging` and then swaps it into `files/content/current`.
- On start the app uses the downloaded pack only if it is newer than the built-in one; an outdated (a newer APK brought
  the pool along) or unreadable downloaded pack is deleted and the built-in pack is used.
- A new pool replaces the deck at once on the home screen or in Settings; during a study session it waits until the player
  returns home. A popup says how many questions were added. Settings shows the pool version and where it came from.

## Alternatives considered

- **A separate content repo or server**: a second place to publish and a second rate limit; the feed already has the
  two channels and the public read access.
- **One request per file with no manifest**: no version to compare, so every check would download the whole pool, and
  there is nothing to verify a download against.
- **Content only in the web version, the app reads it from GitHub Pages**: the pages have no beta channel and would make
  the stable app see untested questions.
- **Apply a new pool only on the next app start**: simpler, but new questions would wait for Android to kill the process,
  which can take days.
- **Server-side (backend) questions**: the app is offline by design (section 1 of DESIGN.md); a pack keeps it offline after
  one download.

## Consequences

- New questions reach both apps and the web version without an APK. A new mode, a new field in the files or a new kind of
  question is still an app release; such a change raises `CONTENT_SCHEMA` (and `SCHEMA` in `tools/content_manifest.py`), so
  older apps ignore packs they cannot read.
- Each check costs one more request against GitHub's 60 per hour per IP; a failed or rate-limited pool check is silent
  and changes nothing.
- Kept true by `ContentPackTest`, `ContentUpdaterTest`, five mutants in `tools/mutation_check.py` and
  `tools/content_manifest.py --check` in the gate (requirements QP-1..QP-4).
- Progress for cards that a correction removed stays in `progress.json` (unused); a backup restored while such cards are
  missing drops their entries, as it did before.

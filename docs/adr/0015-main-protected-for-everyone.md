# ADR-0015: GitHub refuses a commit on main that CI has not passed, also from the owner

- **Status:** accepted
- **Date:** 2026-11-18
- **Type (ISO/IEC/IEEE 14764):** preventive
- **Decided by:** Giovanni ("implement it, but simulate the implementation beforehand") / built by Claude Code

## Context

Main was protected on GitHub since 2026-10-25: a commit needs the CI job `gate` to pass. The rule had an exception for
administrators, and every push is made with Giovanni's account, the administrator. So the rule never stopped anything
(audit of 2026-11-18). The only real check was the pre-push hook on the laptop, which is a setting of one clone, can be
skipped with `--no-verify`, and tests the working folder, not exactly the commit that is sent. Players' phones download
releases built from this repository, so a broken main matters more here than elsewhere.

## Decision

1. The rule applies to administrators too (`enforce_admins`). A plain `git push` of a new commit to main is refused.
2. `tools/push_main.sh` is the way to main: it pushes the commit to a short-lived branch `ci-<commit>`, waits for the CI
   run, moves main to the commit only when the run passed, and deletes the branch either way.
3. The pre-push hook stays: it stops a bad commit on the laptop, before two minutes of CI are spent on it.

Rehearsed first on a copy of main (`sim-main`, same rule): a direct push was refused with "Required status check gate is
expected"; the script moved the branch after a green run (2 min 30 s); a commit that CI rejects left the branch unchanged.

## Consequences

- A push takes about two and a half minutes longer, and CI runs twice per commit (branch, then main). The repository
  is public, so CI minutes are free.
- Any tool that pushes to main some other way is refused. Today none does: the release scripts push to the feed repository.
- The other repositories keep the exception; there the laptop hook is the gate.

## Alternatives

- **Keep the exception:** nothing between a skipped hook and a broken main.
- **Pull requests:** the same guarantee with more ceremony for a single developer.

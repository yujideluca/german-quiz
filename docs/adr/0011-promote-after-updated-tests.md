# ADR-0011: A version may be promoted once it survives the updated automated tests; then Giovanni decides

- **Status:** accepted
- **Date:** 2026-10-22
- **Type (ISO/IEC/IEEE 14764):** preventive
- **Decided by:** Giovanni
- **Amends:** ADR-0005 (the beta channel stays; what makes a version eligible for promote is defined here)

## Context

`tools/metrics.py` showed betas promoted less than a minute after publishing (issue #2). The question was whether a
version must spend time in the beta app before reaching users.

## Decision

The condition for promoting is the automated tests, not time in beta. A version is eligible once it survives the
automated tests **updated for whatever the new version changes** (new rules, new exceptions, new content):

1. the quality gate with the mutation check passes (`publish_apk.sh promote` runs it);
2. the tests were updated: `tools/check_tests_updated.sh` refuses a promote when code or content under
   `core/src/main` or `app/src/main` changed since the last stable release and no test file did (`beta` only warns);
3. by the working rules, each new behaviour is a requirement row with a tagged test (ADR-0008).

From then on, promoting to the user version is **Giovanni's decision**. There is no minimum time in beta, and
Claude Code does not argue for one.

## Alternatives considered

- **Minimum time in beta (for example 12 h)** — rejected: time does not test anything the suite does not.
- **A manual beta checklist on the phone before asking** — not required; the device debug pass after coding stays.

## Consequences

The checks prove the tests changed, not that they are the right tests; the requirement-first rule and the mutation
check cover that. The metric "time in beta" is no longer a warning sign; the review watches the rework rate instead.

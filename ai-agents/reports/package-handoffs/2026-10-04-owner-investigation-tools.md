# OWNER-INVESTIGATION-TOOLS handoff

Status: PARTIAL / ACTIONABLE_CONTINUATION. Explicit owner assignment after LuxStaff review.
Canonical base: `18d4f4b05af94ee325842c68d482feedabe5d27f`; isolated branch `package/owner-investigation-tools`.

Implemented: successful-visit patrol preference, optional observed-idle filtering, bounded session activity with inspector permission, configurable protected hotbar slots, durable/audited create/resolve flags, inspector flags/notes, bounded visible-player join summaries and restart-only config rejection. New permissions default false; join alerts default disabled; flag edits require ACTIVE mode and explicit staff rank. No recovered LuxStaff code is imported.

PR #215's three source/test files are reused from exact head `b0ab306568081fc4afe602db36ddd98355e7affa`; its target-revalidation hardening was already proposed, not merged. No transfer ownership changes from #321 are duplicated. Preserve other open package work and the original owner checkout.

Local evidence checkpoint: Java 25 toolchain, release 21, warnings-as-errors; 1,620 unit tests discovered, zero failures/errors, two existing Windows symlink skips; all four runtime JARs built; new MariaDB test compiles. Paper/Velocity JAR CRC and provider API leak checks pass (27 source contracts, zero leaks). Initial Windows CRLF-sensitive source-inspection failures disappeared with LF working-copy bytes; no unrelated source changes were staged. A mistyped nonexistent Gradle provider-check task was corrected to the repository workflow's actual JAR inspection.

Database test execution: NOT PASSED locally. Testcontainers could not find a valid Docker environment; the existing integration-test Gradle script also cannot serialize its configuration-cache closure. Hosted repository workflow disables configuration cache and provides Docker. No database/staging/live player acceptance is claimed.

Migration: live main V21; #316 and #203 competing V22; #279 V23. Proposed V24 flags must undergo migration-order reconciliation before release. Existing migration files are unchanged.

Next: publish draft aggregate PR; freeze product changes, rerun affected tests and exact-head CI/static/review; address actionable findings. Record PR/head/check results here and in PR text. Staging and client/Bedrock acceptance remain separate. No merge/deployment/restart/production mutation is authorized.

## Published checkpoint

Implementation [PR #322](https://github.com/wsg138/EnthusiaStaff/pull/322), draft, head repository `FainNeito/EnthusiaStaff` (the local Git identity lacks upstream write access). Initial head `77cce0827bd0ada70b6e2445ddea31f06277ee22` had 26 Codacy annotations. Those concrete findings were addressed with method decomposition, private block locks, parameter context and row mapping helpers; reanalysis remains required on the next head.

Local review additionally fixed queued/late patrol work crossing reconnects and overlapping requests, with three new regressions. Affected unit tests and runtime builds pass after the substantive fixes. Orchestration validator has exactly the same 460 errors on canonical starting main and this branch, with zero introduced errors; it is not described as passing. Wiki validator passes 41 pages.

Hosted initial-head build runs Coverage `37258967704`, Sentinel artifact `37258967699`, Wiki `37258967689`, and state-reset proof `37258967687` all stop at `ACTION_REQUIRED` for maintainer approval of fork workflows. No hosted product execution or pass is claimed. Initial live review threads: zero. Database/staging and migration-order gates remain pending. No merge is authorized, including the repository-requested separate docs-only status-publication merge; publish that reviewable PR without merging.

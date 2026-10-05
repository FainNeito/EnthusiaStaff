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

Status-publication proposal: docs-only branch package/owner-investigation-status. Current implementation product head bee3307a3d59b4c5cae6abe411c1f01514c45fae is on draft PR #322. This PR contains only routing/contract/handoff records; no product activation or merge is authorized. Static reanalysis, fork workflow approval, MariaDB/staging and migration sequencing remain required.

## Frozen product evidence and exact next action

Product head `bee3307a3d59b4c5cae6abe411c1f01514c45fae`, draft [PR #322](https://github.com/wsg138/EnthusiaStaff/pull/322), is clean and mergeable but remains validation-incomplete. Clean build with build/configuration caches disabled passed 1,624 unit tests; two existing Windows symlink tests skipped, zero failures/errors (1,626 discovered). All four runtime JARs passed ZIP CRC and provider API leak checks (27 contract types, zero leaks), with own class version 65. MariaDB integration tests compile; execution/staging remains NOT PASSED/NOT RUN.

Exact-head Codacy check `111604879327` PASS, zero annotations after all 26 initial and four follow-up findings were repaired. No human approval is claimed; live inline review threads were zero. Wiki validator PASS, 41 pages. `git diff --check` PASS. Full local diff review checked authorization, bounded state, scheduler ownership, session cancellation, expiry validation and transaction rollback paths.

Exact-head hosted runs Coverage `37259881945`, Sentinel artifact `37259881953`, Wiki `37259882001` and state-reset proof `37259881963` require maintainer approval of fork workflows; Coverage has no jobs, so no hosted product result exists. Pi supersession is skipped. Existing orchestration errors remain 460, identical to canonical starting main; zero introduced errors, not a pass.

Local test artifact SHA-256: Paper `912d002a8150e5ca0d9415b4c50357809bdaa539bc8cf5cf607547ce2df49d8f`; Velocity `08fcbe0e7bb78ad58917a211310e0afc8bbe1c18f529a3d4a9dbd51df09cd5c6`. These unmerged artifacts were not uploaded or activated.

Status publication is draft [PR #323](https://github.com/wsg138/EnthusiaStaff/pull/323), documentation only. Runtime/Pi checks are not applicable to this status PR because it changes only routing, contract and handoff records. Neither PR is merged: the owner's standing agreement withholds merge authorization, overriding the repository's automatic status-merge instruction.

Next action: maintainer approves exact-head fork workflows; inspect resulting tests/static/review; reconcile V22/V23/V24 sequencing with the owning PRs and complete authorized staging/Java-Bedrock acceptance. Preserve frozen product head, other packages and production state until those gates and separate merge/deployment authorizations are satisfied.

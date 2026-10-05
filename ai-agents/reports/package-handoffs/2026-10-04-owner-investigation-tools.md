# OWNER-INVESTIGATION-TOOLS handoff

## Automatic vanish and completion extension, 2026-10-05

Owner requested automatic vanish when entering Staff Mode and completion for all player-name command arguments, explicitly alts. Same package/PR #322; start `85d4db419ee6dcf0266a3cf686a89fb5b96c1e9e`. Refetched main `ba6dcabc9a731e7e3e21c8405778764abdc626f4` contains merged #321 and is normally incorporated as `ebff2f09`. No conflict, reset, user-change overwrite or transfer/recovery policy replacement occurred.

Fresh durable entry invokes an idempotent vanish enable after successful profile publication. Failed activation does not call it; recovered/transferred sessions keep their state. Vanish persistence reports success/failure; failure exits only the same fresh session through existing durable restoration. Queued vanish writes are fenced to their active session when applicable. Existing independent-rank exit semantics remain intact.

Shared Paper player-position routing offers bounded visible local online names, keeps legacy inventory offline completion, delegates non-player inputs, and covers direct and nested moderation/report/inspection/freeze/flag/staff-tool/tester/fake-base plus console-only targets. Folia entity names are cached on owning schedulers/events. Estaff status completer remains unchanged for runtime verification. Velocity alts and both alt targets query bounded known-name/vanish providers on the existing worker executor with eight pending requests maximum, permission rechecks before work/delivery, visibility filtering, invalid-prefix guards, unavailable-provider/rejected-queue empty results and separate reopen gating.

Focused tests cover route positions, reason/confirmation preservation, visibility, revocation, existing offline cache, console restriction, result limits, known offline alts, asynchronous execution, saturation, rejection and provider failure. Vanish entry/failure/session and recovery/handoff wiring is statically verified, not live runtime acceptance. After incorporating #321, its LF-only source-reading test failed on Windows CRLF; normalize read bytes without removing assertions, then the Paper/Velocity tests pass. First compile exposed deprecated metadata API and wrong CommandMap method; corrected using the actual cached Paper API's getKnownCommands contract. No behavioral red/green claim is fabricated for new routing.

Next: freeze current product, run clean local gates and exact-head static/hosted/review, publish the updated partial state via existing docs PR #323. Docker/MariaDB and real Java/Bedrock/Folia/provider/distributed acceptance remain separate, and migration V24 sequencing remains unresolved. No production mutation or merge is authorized.

## GUI extension, 2026-10-05

Owner requested GUI recommendations and streamlining; continue this same package/PR. Refetched upstream main remains `18d4f4b05af94ee325842c68d482feedabe5d27f`; implementation starts from clean existing `bee3307a3d59b4c5cae6abe411c1f01514c45fae`. Other owner GUI and transfer branches remain untouched.

Implemented fixed grouped dashboard tools; permission-filtered player overview/history/flags/client/inventory/ender/punishment shortcuts; separate Close and confirmed Exit; loading cancellation via Back; permission-aware `/stafftools help`. Typed holders retain actor/target UUIDs, target snapshots use owning schedulers, and delayed investigation delivery requires the original inventory still open plus live session/inspect/action permissions and visibility. Mutations remain in existing command/service workflows; no automatic evidence save or punishment. UUID routing is retained except the existing client command's name-only interface.

Proof: focused routing suite first failed two fixed-slot/filter assertions against previous code, then all eight view/routing tests passed after implementation. No historical red/green claim for prior features. Full clean build/unit/runtime checks and exact-head hosted/static/review evidence must follow. Existing MariaDB/Docker, migration-order and live Java/Bedrock acceptance gaps remain; no unchanged unavailable gate is retried. No additional migration or provider API is changed in this slice.

Future unified timeline, durable investigation ownership/handoff, follow-up inbox and evidence bundles are not implemented by these shortcuts. Review existing Discord investigation ownership before introducing persistence. Canonical status is still incomplete until the documentation-publication PR reaches main; merging remains unauthorized.

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

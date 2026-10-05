# Confirm-player fork handoff

## Owner transfer reconciliation verification, 2026-10-05

Same owner package remains PARTIAL / ACTIONABLE_CONTINUATION in draft PR #1.
Frozen executable head 00c5c6481e46b1ec5c3c7d823eafc6257e5a0d11 incorporates
canonical upstream ba6dcabc (PR #321) from owner 61d0712c. Backend-local native
snapshots now detach/rebind with ownership fences; normal travel stays unblocked.
Owner reconciliation retains applied duty-rank mode authority, friendly names,
explicit exit disabling vanish for all ranks, and operation-ticket recovery.
Vanish mode application and selected-mode persistence defer during staff-state
capture/restoration, preserving destination native state and saved selections.

Focused Paper staff/visibility and Velocity handoff tests pass. Full exact-head
Java 25.0.3 clean test/check/runtimeJars passes: 1,646 tests, zero failures/errors,
two Windows symlink skips. Wiki validates 41 pages; whitespace, runtime ZIP
integrity and RoseChat provider exclusion pass. Test.9 artifacts are unmerged/local
tests only. Review covers transaction detach/rebind ownership, rollback, join
ordering, quit/restart recovery, metadata-only protocol transfers and preservation
of prior owner repairs. No migration or external component pin changed; root
aggregate remains the canonical core build. New JDBC integration tests were not
run: Docker/MariaDB remains unavailable. This is not live transfer acceptance.

EARS/state helpers remain absent; bounded requirements/tasks/evidence are retained.
Orchestration baseline remains FAILED with 460 previously unchanged findings;
its inputs/validator are unchanged and the unavailable/baseline gates were not
repeated. Hosted/static checks, database lifecycle integration, staging, provider
runtime and Java/Bedrock client acceptance remain unverified. Next: inspect exact
head review/checks and obtain missing acceptance evidence through normal reviewed
delivery. No product merge, deployment, permission change or authority activation.
Exact delivery heads and artifact hashes are recorded in PR #1. This verification
supersedes the preceding transfer requirement and older build evidence.

## Owner transfer reconciliation requirement, 2026-10-05

Same owner package remains PARTIAL / ACTIONABLE_CONTINUATION in draft PR #1.
From owner 61d0712c, canonical upstream ba6dcabc (PR #321) changes backend
snapshot ownership, detach/rebind, restart recovery and transfer ordering.
Requirement: incorporate local saved-state ownership and non-blocking travel
without losing friendly names, applied duty-mode authority, explicit exit disabling
vanish for every rank, or the operation-ticket recovery fence. Destination native
state must be captured before vanish changes game mode; restoration must not
persist its temporary mode as a new vanish selection. Transfer metadata remains
non-authoritative for inventories and durable staff identity.

Resolve the two overlapping manager files, retain both activeRank and transition
queries, adapt upstream wiring tests to owner mode/exit semantics, and run focused
staff/vanish/transfer tests followed by exact-head Java 25 clean checks, runtime
JAR and Wiki verification. Inspect transaction ownership and protocol/provider
compatibility. No migration bytes or production settings change. Docker/MariaDB
integration remains unavailable and is not retried; new database lifecycle paths
therefore remain locally unverified. EARS/state helpers are absent; this bounded
requirement/task/evidence record is retained. No product merge/deployment.

## Owner durable recovery fence verification, 2026-10-04

Same owner package remains PARTIAL / ACTIONABLE_CONTINUATION in draft PR #1.
Frozen executable head 923e8352753cae527b2e31dd2df1f13eb4a63208 repairs the
confirmed per-player durable recovery race from owner head 44fe28e9. Canonical
upstream main 18d4f4b is unchanged and incorporated; root aggregate owns the build.
A read begun before exit was only guarded by temporary exit/restoration flags;
its late result could reapply vanished state after exit completion. UUID-only
pending cleanup also allowed old callbacks to interfere with a reconnect read.

Recovery now carries a unique operation ticket. Exit/restoration, accepted local
state/mode changes, transfer application, reconnect/disconnect and shutdown
invalidate pending reads; stale callbacks cannot apply or clear replacement
reads. Missing storage, queue rejection, exceptions and retired-owner completion
remove only their own ticket. One ticket per player is retained, with no permanent
generation history. Existing permissions, durable write semantics, API/runtime
contracts and earlier name, GUI, vanish, tester and inspection fixes remain.

Proof: a temporary extraction of prior UUID-only pending-set semantics failed two
reconnect/old-completion regressions; the operation-ticket implementation passes
all five fence tests. Two manager wiring tests verify lifecycle invalidation and
owning-scheduler result/cleanup gates. This is local helper/wiring evidence, not
an actual server/client delayed-read reproduction or a claim of historical TDD.
Full Java 25.0.3 clean test/check/runtimeJars passes at the frozen executable head:
1,642 tests, zero failures/errors, two Windows symlink skips. Wiki validates 41
pages; whitespace, runtime ZIP integrity and RoseChat provider exclusion pass.
Test.8 artifacts are unmerged/local tests only. No schema, dependency pin or
production permission changed. Review checked stale callbacks, cleanup ownership,
concurrent writes, session lifetime and preservation of prior source repairs.

Existing orchestration baseline is FAILED (460 unchanged findings); affected
inputs/validator are unchanged and it was not repeated. EARS/state helpers are
absent; this bounded requirement/task/evidence record remains the fallback.
Docker/MariaDB, hosted/static, staging, runtime-provider and Java/Bedrock client
acceptance remain unverified. Next: inspect exact-head review/check findings and
obtain missing acceptance evidence through normal reviewed delivery. No product
merge, deployment or authority activation is authorized. Exact delivery heads and
artifact hashes are recorded in PR #1. This section supersedes the task above.

## Owner durable recovery fence requirement, 2026-10-04

Same package remains PARTIAL / ACTIONABLE_CONTINUATION in draft PR #1.
Starting owner head 44fe28e9; canonical upstream main 18d4f4b is unchanged.
A retained review concern is valid: the current per-player durable recovery
callback only checks transient exit/restoration flags. A read started before
exit can return after those flags clear and reinstate vanished state/mode.
The UUID-only pending-load set also lets an old connection callback clear a
new connection's pending load.

Requirement: apply a per-player durable recovery result only while its unique
read ticket remains current. Staff exit, accepted local state/mode changes,
transfer snapshots, reconnect/disconnect and plugin shutdown invalidate tickets.
An old completion must never clear a replacement ticket. Keep one pending ticket
per player and remove it on completion/cancellation/storage unavailability.
Preserve existing durable write/authority/permission semantics and prior repairs.

Prove: regression for completed-exit invalidation and overlapping reconnect
callbacks, plus manager wiring verification; implement a small infrastructure
fence and retain owning-scheduler rendering. Full Java 25 clean checks, runtime
JAR integrity and Wiki verification follow a frozen reviewed head. No live-client
claim. EARS/state helpers are absent; this record is the fallback. Existing
unavailable gates remain open and are not retried. No product merge/deployment.

## Owner GUI reconciliation verification, 2026-10-04 22:00 UTC

Owner package remains PARTIAL / ACTIONABLE_CONTINUATION in draft fork PR #1.
Frozen executable head e418edb8c3c62c0e6029041baff8423a91b1fdb3 normally merges
canonical upstream main 18d4f4b05af94ee325842c68d482feedabe5d27f from owner
head dd62fc46. Root aggregate owns the core build; no submodule/dependency pin
changed. Requirement: preserve friendly player labels, actor-owned name
confirmation, UUID identity, prior vanish/provider repairs and privacy/authority
behavior while incorporating upstream target picker, configured ladder and
permission-rechecked history. Redundant UUID player-card lore is removed.

The isolated merge resolved only GUI imports/layout and labels. The new upstream
source-reading security test failed on Windows CRLF; normalizing its input
retains every assertion. Focused punishment/name, GUI security, vanish policy and
optional RoseChat regressions pass (117 tests). No historical behavioral red/green
claim is made for upstream integration. GUI player-label wiring is also checked.
Java 25.0.3 clean test/check/runtimeJars passes at the frozen executable head:
1,635 tests, zero failures/errors, two Windows symlink skips. Wiki validates 41
pages; whitespace, runtime ZIP integrity and RoseChat provider exclusion pass.
Test.7 JARs are unmerged/local test artifacts. No production acceptance is claimed.

Prior orchestration baseline remains a failure (460 findings); its inputs and
validator are unchanged and it was not rerun. Docker integration, hosted/static,
staging, runtime-provider and Java/Bedrock acceptance remain unverified. No EARS
or state helpers exist; this small requirement/task/evidence record is retained.
Local review checked command routing, actor-owned confirmation preservation,
target picker visibility, bounded overview, sensitive-history revocation, ladder
and owning-scheduler rendering. No migration or permission policy changed.

Next: check exact PR head for actionable review/CI findings and obtain missing
runtime/provider/client evidence through normal reviewed delivery. Canonical
product main has not received the owner fixes; product PR merge and production
deployment are unauthorized. Exact delivery heads and hashes belong in PR #1.
This section supersedes the preceding reconciliation task/checkpoint records.

## Owner reconciliation verification, 2026-10-04

Owner package remains PARTIAL / ACTIONABLE_CONTINUATION in draft fork PR #1.
Frozen executable head f0ad2a7527e2960fa84ef8910a083fc77fb97094 is pushed to
package/owner-punish-confirm-player. Canonical upstream main 93e81ca1 is
incorporated by normal merge. Applied duty profiles now own vanished game-mode
selection/recovery/restoration; off-duty vanish policy and earlier name, item
safety, visibility and exit fences remain. Optional RoseChat presence rendering
fails narrowly and once on older interface binaries without disabling the bridge.
Focused regressions reproduced both defects before repair; nine focused tests pass.

Java 25.0.3 clean test/check/runtimeJars passes at the frozen head: 1,627 tests,
zero failures/errors, two Windows symlink skips. Docker-dependent integration
suite was explicitly excluded, not passed. Wiki validates 41 pages; whitespace,
Paper/Velocity ZIP integrity and RoseChat provider-class exclusion pass.
Orchestration validation FAILS with 460 findings; isolated canonical upstream
comparison reports the identical 460 findings, with zero new findings. Do not
weaken the validator or invent registry entries to hide the baseline failure.
No project EARS/state helpers exist; this requirement/task/evidence record is
maintained instead. Hosted/static, MariaDB, staging, actual provider compatibility
and real Java/Bedrock acceptance remain unverified. Unchanged unavailable gates
were not retried. Test.6 artifacts are unmerged/local test artifacts only.

Review inspected the synchronized source delta, duty authority composition,
entity-scheduler callbacks, durable mode retries, optional API linkage and earlier
repair preservation. Exact-head external review/check state is recorded in PR #1;
local review is not hosted approval. No schema migration changed. Canonical core
build is the root aggregate; standalone component parity/runtime acceptance is
still required where affected.

Next: inspect new exact-head review/check findings, repair confirmed owner-scope
defects, and obtain missing provider/runtime and client acceptance evidence through
normal reviewed delivery. Canonical product main is not updated by this fork PR.
Do not merge product PR #1, deploy, change production permissions or activate
authority. This section supersedes older isolated-candidate status below.

## Owner reconciliation repairs, 2026-10-04 follow-up

The same owner package remains PARTIAL / ACTIONABLE_CONTINUATION. Canonical
upstream 93e81ca1 is reconciled in the isolated candidate, with earlier player-name,
tester safety, visibility, and exit-fencing fixes retained. The duty/vanish mode
conflict is repaired: on-duty selection, recovery, restoration and queued-write
validation use the applied duty rank and canonical mode policy. Off-duty vanish
retains its previous policy. Helpers may use Survival/Spectator on duty;
Mods/Developers remain Survival; Admin/Founder permitted modes remain intact.
No permission backend or production authority was changed.

An older RoseChat interface binary reproduces NoSuchMethodError for the new
optional presence-render method. A narrow compatibility adapter now disables
only that optional rendering path once and retains the existing active bridge.
The focused policy and binary-compatibility regressions first failed as expected,
then all nine focused tests passed. Runtime matching/live acceptance remain open;
this synthetic old-API binary proof is not a production-provider acceptance test.

The source repairs follow local candidate c8bf330d and repair commit d7588734.
Full exact-head Java 25 clean test/check/runtimeJars, Wiki, orchestration and
artifact verification must complete before the shared PR branch is updated;
actual frozen head and results belong in PR #1's verification record. Test artifact
version is 0.1.0-staff-bugs-test.6, explicitly unmerged/local test only. Docker,
hosted/static, staging and client acceptance remain unverified. No unchanged
unavailable gate is retried. No EARS/state helpers exist; bounded requirements,
tasks and evidence are recorded here and in the canonical owner handoff.

Next: finish complete synchronized-diff review and available exact-head local
gates, then push reviewed source to existing draft PR #1. Publish the partial
status through the allowed docs-only PR; do not merge the product PR, deploy,
change production permissions or activate authority.

## Historical isolated upstream reconciliation proof, 2026-10-04

Continuation requirement: on-duty vanish must use the applied Staff Mode rank and
its canonical game-mode policy; off-duty vanish keeps its existing policy.
Helper Survival must remain selectable, Mod/Developer must remain Survival, and
Admin/Founder permitted modes must survive both paths. Missing applied authority
must fail closed. Older RoseChat binaries without the optional presence-render
method must not propagate a linkage error or disable the existing bridge.
Establish focused failing regressions before these repairs, then verify the
synchronized exact head and retain the separate runtime/client acceptance gaps.

This section records the initial local candidate before the later repair checkpoint.
Starting owner head: `14dbab0fb63ad8b332cfd2bb82dd5da3eefdee6f`.
Canonical upstream: `93e81ca1d4a1d2ce4f1199c91130bb7963093dcc`.
Worktree: `EnthusiaStaff-upstream-proof`, branch `package/owner-staff-upstream-proof`.
The automatic merge has no textual conflicts. Requirements: retain names,
actor-bound confirmation, safe tester recovery, exit fencing and permitted
Admin/Founder modes; reconcile canonical on-duty rank policies and companion API
compatibility before pushing any candidate. Existing tests are regression evidence,
not historical red/green proof. No EARS/state helper was located in this checkout;
this record tracks the bounded requirement/task/evidence instead.

Initial focused build failed at Paper compilation: the new transfer hook calls
removed `enforceVanishSpectator`. Route that hook through the retained authorized
vanish mode implementation, then rerun the relevant module suites. Further review
must resolve the overlap between canonical on-duty mode profiles and the fork's
vanish mode policy, and verify RoseChat's new presence contract against the actual
supported provider. No production, shared branch, or authority changes.
## Owner upstream reconciliation checkpoint, 2026-10-04

Owner package remains `PARTIAL` / `ACTIONABLE_CONTINUATION`. Product PR #1 remains
open/draft at `14dbab0fb63ad8b332cfd2bb82dd5da3eefdee6f`; its two existing review
threads are resolved. Canonical upstream was fetched at
`93e81ca1d4a1d2ce4f1199c91130bb7963093dcc`.

Isolated local candidate: branch `package/owner-staff-upstream-proof`, worktree
`EnthusiaStaff-upstream-proof`, checkpoint
`c8bf330d16618808b678bc1fff0fc6c97f6bc0bb`. It is not pushed, merged into canonical
main, a release, or a deployment. Automatic source merge was conflict-free but
Paper compilation failed because the transfer hook referenced a removed method.
The hook now routes through the retained authorized vanish-mode implementation.
Four source-wiring tests failed on Windows CRLF and their source readers now
normalize line endings without removing assertions. Selected Paper, Velocity,
domain and protocol suites then report 1,225 tests, zero failures/errors and one
existing Windows symlink skip. No new full-build, hosted, Docker, staging, runtime
artifact or client acceptance pass is claimed. EARS/state helper tooling was not
located; bounded requirements/tasks/evidence are maintained in the candidate handoff.

Next: resolve the overlap between canonical on-duty game-mode authority and the
fork vanish policy (Helper Survival and Mod/Developer Survival), preserving
Admin/Founder selections, names, tester safety and exit fencing; establish
cross-policy regression evidence. Verify the new RoseChat presence API against
the supported provider runtime before acceptance. An available older source
snapshot lacks that API; this is not production-version evidence. Then review
and validate the complete synchronized candidate before pushing source to existing
PR #1. Upstream routing remains unchanged. No product PR merge, production change,
permission change or authority activation is authorized by this checkpoint.

## Review follow-up (2026-10-03, 08:15 UTC)

Owner confirmation: the reported environment is production; no fork deployment or production verification is implied. Baseline PR head: `f1999bd144a8cddee1121a4773a97eb301c8bbbc`. CodeRabbit run `d3c2adc6-c7ea-44cd-b588-bd351600f720` completed with two valid findings, both repaired in the current checkpoint: canonical UUID confirmation first checks for an actor-owned draft and otherwise resolves the target-bound draft; loose UUID-like names are not coerced. Queued offline inventory edits now recheck view permission and staff identity alongside edit permission and an active session on the owning scheduler. Added regression coverage passes the Paper suite. Frozen repair and package-record head: `73f04425996a3b1fab67062ffc7b8e2dfdc69caf`. Java 25.0.3 clean all-module unit test/check/runtimeJars passed: 1,580 tests total, two existing Windows skips, zero failures/errors, Docker-dependent :integration-tests:test excluded. Final synchronized head and artifact hashes are recorded on PR #1. Version: `0.1.0-staff-bugs-test.5`.

The package remains `PARTIAL` / `ACTIONABLE_CONTINUATION` with the same live/provider/integration acceptance limits. No GitHub Actions runs exist for the inspected baseline; a CodeRabbit review success is not runtime acceptance. Next: push verified repairs, resolve the addressed review threads, publish updated status through a docs-only PR, and monitor the subsequent review. Product merges, deployment, production permissions, data access and authority activation remain excluded.


## Current owner scope: staff bug fixes (2026-10-03)

The owner expanded the same fork package to address all staff bug reports and requested a PR plus periodic commit checks. Status: `PARTIAL` / `ACTIONABLE_CONTINUATION`; implementation remains in draft [PR #1](https://github.com/FainNeito/EnthusiaStaff/pull/1) on `package/owner-punish-confirm-player`. Prior player-name and vanish shortcut changes are retained. Historical scope and evidence below describe earlier checkpoints, not current completion.

Implemented this checkpoint: namespaced staff tool commands (inspector collisions); non-destructive totem preparation and restoration despite evidence/checkpoint failures; staff exit clears vanish for every rank and fences delayed game-mode recovery; guild invite command presence protection; explicit staff identity plus view permission for inspection; active staff-mode visibility privacy on Paper and Velocity, including public counts. Focused Paper/Velocity tests pass. Test artifact version: `0.1.0-staff-bugs-test.4`. Frozen product and package-record checkpoint: `a93b81ed8bec60ae7c95000d27acf824fa50173d`. Java 25.0.3 clean test/check/runtimeJars passed there: 1,576 tests, zero failures/errors, two existing Windows symlink skips; `:integration-tests:test` excluded because Docker is unavailable. Runtime ZIP integrity passed. Final exact head and artifact hashes are recorded on PR #1. Wiki line endings were repaired after its validation rejected three modified pages.

Remaining acceptance: reproduce freeze movement/reconnect on the current build (new reconnect/fail-closed tests pass, no proven original cause); verify staff hotbar, wall traversal, F3+N, exit restoration, tab/count/guild surfaces and tester inventory recovery with Java/Bedrock clients; Docker/MariaDB and hosted/static/staging checks remain unverified. Older reports involving legacy staff commands, permissions, chat/note/join formatting, punishment broadcasts/hover, compass teleport, combat-log behavior, random-teleport exemptions and reward items require provider/version attribution. Existing safeguards are not claimed as live fixes. No production changes or authority cutover were performed.

A thread heartbeat checks commits, CI and review findings every 30 minutes; it stays quiet without meaningful changes and continues authorized actionable repairs. Public records contain sanitized technical findings only, with no private report transcripts, player evidence or private channel links. Next action: validate and push this checkpoint, publish this partial state through a docs-only PR, then address concrete review failures or newly reproducible in-scope defects. Preserve upstream routing and the implementation PR.


Owner-assigned work: allow `/punish confirm <player>` in `FainNeito/EnthusiaStaff`.
Base `0aaefc4a22415f6af7778887cdab7d4b6f40468a`; branch
`package/owner-punish-confirm-player`. No prior handoff exists for this owner request.

Implementation and documentation are complete. The command resolves the actor's
current target-bound stored draft on the worker pool and then retains the original
confirmation path. V9 enforces one draft per actor/target. Name prompts, completion,
offline directory lookup, Bedrock prefixes, and original draft-ID compatibility are
covered. No provider or persistence implementation changed.

Initial domain/Paper tests and both runtime JAR builds passed on Java 25.0.3.
Final all-module unit/build/check/runtimeJars validation passed with
`:integration-tests:test` explicitly excluded: 1,554 tests, zero failures/errors,
two existing Windows symlink tests skipped. The final Paper tests/build also pass.
Paper test artifact SHA-256 is
`702b4b1857949aa1a023f5adb5132e5432fe434c301dd17995bac44e1a82d1b2`.
Docker is unavailable locally;
MariaDB/Testcontainers integration and live Paper/Bedrock/multi-backend acceptance
have not run. Wiki validation passes all 41 pages. No hosted/static/staging pass is
claimed. No production state or upstream branches were changed.

Draft fork PR [#1](https://github.com/FainNeito/EnthusiaStaff/pull/1) is open at
product head `b22ce0077f4059a73de847dd09ecc84862c88a44`. Its branch is pushed and
preserved. GitHub reports zero check runs at inspection; hosted validation has not
executed. The connected GitHub integration cannot write to this fork (HTTP 403),
so the authenticated owner browser was used to create the PR.

Next: run the applicable hosted/integration acceptance once the fork's validation
environment is available, keeping missing evidence explicit. Do not merge
the implementation before the applicable gates pass or deploy it to production.

## Owner scope expansion, 2026-10-03

The owner expanded this same fork work item to show names for player identities
throughout staff-facing Minecraft output, including the reported Velocity alt
review. Starting continuation head: `fba6b0ed09ce6b50e8c534488a820f6c13282e98`;
current target `main`: `6374a5c9e97ad5b8e6d15f80b13c77da1511423f`.
Status remains `REVIEW`; draft fork PR #1 and its implementation branch are preserved.

Completed: network alt identities and unnamed verified links; report targets,
reporters and assignees in text and menus; case subjects and missing actor-name
snapshots; history subjects and page prompts; freeze staff identities and inspector
status; punishment and sanction target menus. Known names no longer carry redundant
UUIDs. Unknown identities explicitly retain their IDs, and ambiguous historical
name matches keep exact selection. Moderation-record IDs and internal UUID keys
remain authoritative. No user-authored evidence text is rewritten.

Directory reads run on existing storage workers. The per-response lookup is bounded
to 512 distinct identities and memoizes repeated players, without a long-lived stale
name cache. Report menus receive immutable name snapshots on the entity scheduler,
with the existing current-load token and permission gates. No persistence migration,
provider API, production permission, deployment, database access, or upstream change.

Validation: Java 25.0.3 clean unit build/check/runtimeJars passed, followed by the
final all-module unit/check/runtime JAR build after inspector/linked-name refinements.
1556 tests pass; 2 existing Windows symlink tests skip; zero failures/errors.
Both JAR ZIPs are intact; Wiki validates 41 pages. The full scoped diff was reviewed
for worker ownership, bounds, missing directory behavior, identity selection,
historical-name preservation, report stale-load rejection, and permission isolation.
Name/rename/Bedrock/missing/bounded-query regressions and alt/freeze presentation tests pass.

Artifacts: `EnthusiaStaff-Paper-0.1.0-player-names-test.2.jar`, SHA-256
`06f2ee1812c9355627e034d39f0741861914fcd5309ba92792989bf858ddb899`;
`EnthusiaStaff-Velocity-0.1.0-player-names-test.2.jar`, SHA-256
`bef4f22ecfad4c5e248b1b8495418ebbfe788326c78fddcab96142f17a24b90f`.
The proxy JAR is required for `/alts`; a backend-only update cannot change that output.

Remaining: Docker/MariaDB integration is explicitly excluded because Docker is
unavailable; hosted/static review and live Paper/Velocity/client acceptance remain
unverified. CodeRabbit skipped draft review. Next action: run the applicable
non-production acceptance and available hosted/integration gates before merge;
do not deploy, change LiteBans authority, or touch production. Final exact head
is recorded on PR #1, avoiding a self-referential tracked-file loop.
`Expanded frozen product head: db067d1a656ab08a2baac2f8bccffcf39e533101 (draft fork PR #1).`


## Owner vanish and spectator continuation, 2026-10-03

The owner confirms the hotbar, wall movement, and F3+N reports came from production
with the original plugin. This explicitly expands the same owner package and draft
fork PR #1; status remains REVIEW. Continuation starts at 0e603ebeabcc8f9af6cc9ded716fae039fe4d6ab,
with fork main dfab3b3203906c497b8889d161085757062c052a. Previous name features remain included.

Confirmed source defect: vanish forced even Admin/Founder into actual spectator and
cancelled their permitted mode changes. Vanish now applies the existing rank-authorized
selection, preserves Survival/Creative hotbars, records only committed mode changes,
and retries the latest selected mode after an in-flight persistence write completes.
Recovery and demotion reconcile against live rank; lower staff remain spectator-only.
The Admin/Founder F3+N/F3+F4 capability packet requires existing minecraft.command.gamemode
permission, grants no operator/server permissions, and revokes on rank/permission loss.
Real operators retain their actual client status. All player calls use entity ownership.

Wall movement cause remains unproven on production. The fork already preserves the
viewer's own SPECTATOR packet for client physics; its regression tests pass. This
build includes that safeguard, but actual wall movement requires client acceptance.
No production access, deployment, permissions, authority cutover, or migrations changed.

Java 25.0.3 clean test/check/runtimeJars passed with integration-tests:test explicitly
excluded: 1560 passing tests, 2 existing Windows symlink skips, zero failures/errors.
Wiki validates 41 pages; whitespace and runtime ZIP integrity checks pass. Reviewed
mode authority, vetoed events, durable selection concurrency, packet-only permission
hint, thread ownership, existing names scope, and recovery boundaries. Docker/MariaDB,
hosted/static review, and live Paper/Velocity/client acceptance remain unverified.
CodeRabbit draft review is skipped, not an acceptance pass.

Latest test artifacts: EnthusiaStaff-Paper-0.1.0-staff-mode-test.3.jar SHA-256
c1931b1e7c1b3428f5f9e437ca7fa5b8ebfded713781f4ebd8bcadccc55b506e;
EnthusiaStaff-Velocity-0.1.0-staff-mode-test.3.jar SHA-256
bef4f22ecfad4c5e248b1b8495418ebbfe788326c78fddcab96142f17a24b90f.
Next action: non-production hotbar, spectator wall movement, F3 shortcut, permission
revocation, restart, and names acceptance plus available integration/hosted gates before
merge. Preserve draft PR #1 and implementation branch; exact frozen heads are on the PR.

Proof result: selected Paper/Velocity/domain/protocol suites report 1,225 tests,
zero failures/errors and one existing Windows symlink skip. Paper compilation
first failed on the removed transfer-hook method and was corrected locally.
Four source-wiring tests then failed solely on CRLF; their source readers now
normalize CRLF while retaining their assertions. The selected suites pass after
those repairs. This is not a clean full-build, hosted, Docker, staging or client
acceptance result, and no runtime JAR was delivered.

Pending merge review finding: canonical StaffModeAccessPolicy permits Helper
Survival and requires Mod/Developer Survival on duty; the retained fork
VanishGameModePolicy still rejects those selections and its periodic reconciliation
can force Spectator. Reconcile active-duty ownership without changing off-duty
vanish behavior or broadening permissions, and add cross-policy regression proof.
The new BridgeRegistration.renderPresence contract is absent from the available
rosechat-26.2 source snapshot; verify the supported runtime binary or provide a
safe compatibility path before accepting the integration. This snapshot is not
production-version evidence. Shared PR #1 remains at 14dbab0; both existing review
threads are resolved and no new head was pushed. Local candidate remains PARTIAL.

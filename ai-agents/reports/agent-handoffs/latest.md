# Latest agent handoff

## Owner ProtocolLib split-packet continuation, 2026-10-06

Same owner package / draft fork PR #1. Start owner 8cd6ba521151cbfbdd28a458c4180839d14df4a1; target fork main 711f0fcec08c84c9612d44524119c7bb5382375f is already contained. Canonical main advanced from ceb12e0f to 672fa3d0bf7f832d207a2f2a7b336b1dee2ec191 with relevant tester repair #337 (f1615b23 plus tests 912ed7a6). The older adapter registers only USE_ENTITY and requires a legacy action wrapper; split ATTACK / USE_ENTITY packets can therefore be omitted or ignored. This is source-level regression evidence, not a reproduced live incident.

Requirement PL-01: receive dedicated attack packets when available without reading the legacy action wrapper; retain legacy USE_ENTITY action parsing when the optional ATTACK field is absent. Preserve tester scheduler/restoration fencing, player-name completion, vanish and backend inventory ownership. Implement only the two reviewed #337 adapter/test files from canonical main; do not merge the previously excluded unrelated integration/authority changes. No dependency/API version change. Validate focused tester/Staff/visibility/command suites, then clean all-module test/check/runtimeJars at the frozen product head, plus Wiki/diff/JAR/provider checks. Existing unavailable Docker/MariaDB, hosted/static, staging and client gates remain open. Existing EARS/state helpers remain absent; this record is the bounded spec/task/evidence fallback.

Inventory and TEST correlation still awaits username/time/Staff sequence and command-vs-menu surface. Production, inventory evidence and backups remain untouched. Status PARTIAL / ACTIONABLE_CONTINUATION; next apply the canonical split-packet repair and validate before pushing to PR #1.

## Consolidated inventory isolation and TEST visibility investigation, 2026-10-05

Owner consolidated the ongoing Staff work here and added two production reports: Hub inventory appearing on SMP and TEST visibility requiring Staff Mode. Continue the same owner package and draft fork PR #1; no competing product PR. Authoritative main `ceb12e0f370db2fe099e7450661df212cac7a4d7` and owner `ceba9a96` were fetched/inspected; executable remains frozen at `a5841e005e20786e6e15b7f02d9021ac4f8d50a0`. Preserve all prior owner protections. No executable change or runtime acceptance is claimed by this checkpoint.

Read-only findings: the preserved older Paper artifact has an unsafe exit path that verifies snapshot checksum but not backend ownership before restoring inventory; its quit listener only clears runtime state. The newly downloaded Paper artifact contains backend ownership checks and native-state quit restoration/detach. Correct Hub/SMP IDs were verified. The currently observed SMP startup predates the newer on-disk JAR, so disk contents do not prove active runtime code. Exact incident attribution and deployed-source equality remain unproven. No known inventory synchronization JAR appeared in the inspected listings; custom provider/Skript paths remain possible.

Active Hub selector defines only SMP and no Staff Mode requirement. Current proxy file registers TEST with server command enabled and queue disabled. An earlier effective-permission check was for a particular account and cannot prove another account/context. Missing command-vs-menu surface, username, timestamp and entry/exit sequence prevent a supported visibility or inventory fix. InventoryRollbackPlus and an existing off-site backup are available; contents were not read or restored. Raw player logs/configuration and reconstructable private evidence stay local and out of GitHub. No console command, production database/player-data access, inventory restoration, permission change, deployment, reload/restart or authority activation occurred.

Requirements: INV-01 backend-local snapshot ownership must be verified before mutation, including exit, recovery and transfer; INV-02 do not replace native destination inventory with source-backend state; TEST-01 allowed TEST access/completion must not depend on Staff Mode. These clarify existing owner bug scope, not new unapproved access policy. SPEAR spec/proof is limited to source/artifact inspection and sanitized operational evidence; engine/behavioral test changes do not apply to this documentation-only diagnostic slice. Existing EARS/state tooling is absent. Prior 1,699-test local evidence remains historical for the unchanged executable; no new runtime/client acceptance.

Status PARTIAL / ACTIONABLE_CONTINUATION for evidence correlation when input arrives. Next: obtain affected username/time, Staff Mode transfer/exit sequence, selector surface and direct TEST response; correlate the locally retained logs with the relevant runtime, then verify backups/snapshots only under authorized access. Existing reviewed transfer restoration repairs already exist in the owner/current source; do not recreate them or activate the replacement without explicit authorization. Docs-only status publication does not authorize merging product or status PRs.


## Owner tester handoff reconciliation verification, 2026-10-05

Same bug-fix owner package remains PARTIAL / ACTIONABLE_CONTINUATION in draft PR #1.
Frozen executable head a5841e005e20786e6e15b7f02d9021ac4f8d50a0 normally merges
relevant canonical tester repair d545d9a9404d8da94c42021bbc5628ccdee1a8fd (#329)
from owner fa4792d1. Authoritative main 4651cc77 was inspected; later PR #330
Discord role-sync/authenticated console bridge is separate integration scope,
not required by this bug-fix package, and is deliberately not incorporated.
A future affected change must re-evaluate that base delta; no authority activation.

Tester begin/finish/recovery/restoration and fake-base continuations use current
players on owning schedulers. Retired, rejected, offline and duplicate completion
paths settle once. Conflict resolution retains owner evidence failure markers
and finally-based checkpoint/restoration priority while adopting the upstream
current-player Consumer callback; stale captured Player references are removed.
Existing offhand, known-name, confirmation, applied-duty, explicit-exit, vanish
operation-ticket and transfer fixes are preserved. Added wiring assertions retain
the restoration wrapper around the new callback; helper tests exercise deferred
online reads, offline retirement, rejection, duplicates and scheduler exceptions.
This is local helper/wiring proof, not historical red/green or live Folia acceptance.

Focused tester suite and full exact frozen-head Java 25.0.3 clean test/check/
runtimeJars pass: 1,699 tests, zero failures/errors, two Windows symlink skips.
Wiki validates 41 pages; whitespace, JAR CRC and RoseChat provider exclusion pass.
Test.11 artifacts are unmerged/local only. Root aggregate owns the build; no new
migration, dependency pin, provider interface or runtime configuration changes.
Existing canonical V24 release/migration sequencing, Docker/MariaDB execution,
hosted/static, staging, provider and Java/Bedrock/Folia acceptance remain open.
Prior integration classes compiled at b96453c7; none changed in this reconciliation.
EARS/state helpers remain absent. Orchestration remains FAILED at the previously
compared 460 canonical/owner findings; substantive validator inputs are unchanged,
so it and unavailable gates were not repeated. Bounded requirements/evidence stay
in this package. Next: inspect exact-head CI/reviews and obtain missing acceptance
through normal reviewed delivery. No product merge, deployment or permission
change. Exact delivery heads/hashes are recorded in PR #1. This supersedes earlier
build verification for this selected package.

## Owner tester handoff reconciliation requirement, 2026-10-05

Same bug-fix owner package remains PARTIAL / ACTIONABLE_CONTINUATION in PR #1.
Starting owner fa4792d1; authoritative main 4651cc77 includes relevant tester
ownership repair d545d9a9 (PR #329) and unrelated Discord role-sync/console bridge
PR #330. Incorporate the tester repair by normal merge; PR #330 adds separate
integration/authority scope and is inspected but not required by this package.
Do not activate authority or replace independent work. Preserve all prior names,
vanish, duty, transfer, confirmation and occupied-offhand repairs.

Resolve tester conflict by combining upstream current-player Consumer handoff
with owner capture failure marker and finally-based checkpoint/restoration guard.
Recovery/start/finish/restoration/fake-base continuations must perform live reads
only on owning schedulers, and retired/rejected work settles once. Run focused
handoff/tester plus restoration wiring, freeze, clean Java 25 gates, runtime JAR
integrity/provider exclusion and Wiki. Keep database, hosted/static, staging,
provider and live client acceptance separate. EARS/state helpers remain absent;
this bounded requirement/task/evidence record is retained. No product merge,
production mutation, permission changes or authority activation.

## Owner investigation reconciliation verification, 2026-10-05

Same owner package remains PARTIAL / ACTIONABLE_CONTINUATION in draft PR #1.
Frozen executable head b96453c7282024ab54b520b660da8a94791d534c incorporates
canonical main 72529729983d01b700bad2de3956d64c1286f101 (PR #326) from owner
33db2038. Investigation tools, configurable layouts, patrol/activity safeguards,
name completion and automatic fresh-entry vanish are retained. Reconciliation
preserves activeRank plus activeSessionId, owner vanish operation-ticket
invalidation alongside session-scoped writes, explicit exit for all ranks,
duty-mode authority, native transfer snapshots and known-name output. Upstream
and owner status sections are retained, with this selected owner package first.

Confirmed completion regression: shared root completion omitted confirm and its
second argument delegated to the old unfiltered completer. Two added tests fail
against the merged pre-fix routing and pass after routing confirm through bounded,
permission/visibility-filtered names and retaining the root keyword. Exact draft
ID execution fallback remains unchanged. This is local completion proof, not
client acceptance. Existing recovery wiring tests track the new future-returning
set/persist method signatures without weakening lifecycle assertions.

Focused Paper command/staff/visibility, Velocity and domain investigation tests
pass. Full frozen-head Java 25.0.3 clean test/check/runtimeJars passes: 1,692 tests,
zero failures/errors, two Windows symlink skips. Integration test classes compile;
Docker/MariaDB execution was not retried and is NOT PASSED. Wiki 41 pages,
whitespace, runtime ZIP CRC and RoseChat provider exclusions pass. Test.10 JARs
are unmerged/local test artifacts only. Review includes command/GUI authorization,
owner scheduling, patrol cancellation, bounded completion, transaction rollback
and previous owner repairs. Core build remains root aggregate; provider interfaces
and component pins are unchanged. Canonical added V24 is incorporated byte-for-
byte; prior migration files are unchanged. Its cross-PR migration ordering/release
gate remains open; no upgrade or production migration acceptance is claimed.

Changed package-state inputs justified a new orchestration comparison: owner and
isolated canonical main each report 460 findings, with zero introduced/missing
findings. The validator is FAILED, not passed. EARS/state helpers remain absent;
bounded requirements/tasks/evidence remain the fallback. Hosted/static, database,
staging, provider runtime and Java/Bedrock/Folia acceptance remain unverified.
Next: inspect exact-head CI/reviews and obtain missing acceptance through normal
reviewed delivery. No product merge, production deployment, permission change or
authority activation. Exact delivery heads/hashes are recorded in PR #1. This
section supersedes earlier verification/build records for the selected package.

## Owner investigation reconciliation requirement, 2026-10-05

Same owner bug-fix package remains PARTIAL / ACTIONABLE_CONTINUATION in PR #1.
Start owner 33db2038; canonical main 72529729 (merged PR #326) adds investigation
tools, player-name completion and fresh staff-entry vanish. Incorporate these
base changes while preserving known-name output, namespace routing, inspection
authority, applied duty profiles, explicit exit disabling vanish for all ranks,
operation-ticket recovery and backend-local transfer snapshots. Entry failure
must retain upstream session-scoped rollback; temporary restoration must not
persist a vanished selection. Resolve source/state overlaps without overwrites.

Review new command completion/GUI authorization, asynchronous owner scheduling,
patrol cancellation and migration V24 compatibility. Run focused command,
visibility, staff and Velocity tests, then freeze and run clean Java 25 checks,
runtime integrity/provider exclusions and Wiki. Database migration/lifecycle,
hosted/static and live runtime acceptance remain unverified; unchanged Docker
unavailability is not retried. EARS/state helpers are absent; this bounded record
is the requirement/task/evidence fallback. No product merge or deployment.

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


Fork-local owner request, 2026-10-03: player-name punishment confirmation is
`REVIEW` in draft [fork PR #1](https://github.com/FainNeito/EnthusiaStaff/pull/1),
product head `b22ce0077f4059a73de847dd09ecc84862c88a44`. Canonical fork handoff:
`ai-agents/reports/package-handoffs/2026-10-03-owner-punish-confirm-player.md`.
Local unit/build verification passed; hosted/integration acceptance remains
unverified. The upstream handoff below is retained as historical upstream routing.

2026-10-05 current extension: same investigation-tools package now includes fresh-entry automatic vanish and player-name completion, based on normally incorporated `ba6dcabc` / merged #321. Use IT-09..10 and the canonical handoff; old GUI-only heads remain historical.

2026-10-05: owner extended the same investigation-tools package with GUI streamlining. Follow the canonical investigation handoff and IT-06..08. New work stays in PR #322; no merge/deployment authorization.

Owner-directed current work: **OWNER-INVESTIGATION-TOOLS — PARTIAL / ACTIONABLE_CONTINUATION**. See [canonical handoff](../package-handoffs/2026-10-04-owner-investigation-tools.md) and [contract](../../work-packages/packages/OWNER-INVESTIGATION-TOOLS.md). This supersedes routing for this worker only; the historical Market handoff below is preserved. No merge or deployment authorization.


Current handoff: **ES-X03 — EnthusiaMarket destructive provider** — **PARTIAL / ACTIONABLE_CONTINUATION**.

Canonical package handoff:
ai-agents/reports/package-handoffs/2026-09-22-es-x03-marketcase-completion-validation.md.

Market [PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) is
OPEN/DRAFT/CLEAN on `package/es-x03-market-static-remediation` at
`81b14c349be0ad404edeedbac5e109e2a375c255`; Staff
[PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) is
OPEN/non-draft/UNSTABLE on `package/es-x03-market-provider` at
`f6732816f35e3a9634068badb56c220b5d679bd4`. Clean-clone component comparison
found no product-file delta and hash
`e7082c5bb1aacbcd95ac8457aa17392a740c3fe5df6154e743eebb4bc6019839`.

Market hosted runs `35601165548` and `35601165577` passed. Exact Staff Coverage
`35733465364` / job `106764602834`, Sentinel artifact `35733465456` / job
`106764605492`, and durable Sentinel restart job `516` (`PAPER_RESTART_OK`)
passed. Codacy Diff Coverage and Coverage Variation passed. No live review
thread remains.

Codacy static check `106765372218` remains `ACTION_REQUIRED` with 1,141
reported issues. Canonical Pi `35733463593` / job `106764599729` stopped before
private dispatch when its workflow-history lookup returned HTTP 401 Bad
credentials; no private Pi, Paper, or MariaDB runtime ran.

Continue only small paired fixes for validated static findings, preserving
Market #7, Staff #139, and exact component parity. The staging owner must
repair the least-privilege bridge credential before a fresh canonical Pi run.
Do not use a personal credential or bypass the public bridge.

No production listing, balance, item, player data, database, deployment,
authority, LiteBans, cutover, or issue #43 acceptance was performed. D09
remains preserved while X03 owns branch-local V21.

Owner update 2026-10-03: the same fork PR #1 now covers known player names in
Minecraft alt reviews, reports/menus, case/history/freeze output, and punishment
menus as well as named confirmation. Status remains REVIEW. Current exact head
is recorded on the PR; the prior `b22ce00` head is historical. Updated canonical
handoff and package record describe local test artifacts and the remaining
unverified integration/hosted/live gates. Upstream routing is unchanged.
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

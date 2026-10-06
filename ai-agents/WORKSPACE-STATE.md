# Workspace state

## Owner asset login routing continuation, 2026-10-06

Start owner 72497684237741a31640a00c79a815cb41c6f158; fork main 711f0fce unchanged; canonical main advances to 1cae3bb31be12b7a85a59490ac2d40fcfdb20873 (#351). Existing owner-head Coverage/runtime proof/Wiki/Sentinel artifact checks passed; Codacy upload, draft review and Pi cancellation remain skipped. PR #1 is draft with zero unresolved review threads.

Requirement AL-01: initial login with protected asset ownership must select the registered owning backend before Staff reconnect routing; AL-02: conflicting asset owners or an unavailable owner must deny initial admission with an explicit disconnect, preserving journal records; AL-03: ordinary backend switches retain owner fences. Prove: three imported executable canonical regressions fail on the unchanged implementation (21 tests, 3 failures): available inventory owner routing, asset owner precedence over Staff reconnect, and immediate disconnect for unavailable owner. These are source regression evidence, not attribution of the reported production overwrite.

Task: adapt only the canonical Velocity routing and denial changes, retaining current store interfaces and journal semantics. Exclude canonical automatic abandoned-edit terminalization: no confirmed owner incident requires changing queued edits or leases. Add conflicting-owner, economy-owner and ordinary-switch regressions; run focused tests and clean aggregate gates. Existing EARS/state helpers are absent; this is the bounded requirement/task/evidence record. Root aggregate owns build; no schema, dependency/provider interface or external component pin changes.

Implementation/architecture/refine: adapted Velocity routing only. With no asset owner, existing Staff reconnect applies; with an asset owner it cannot redirect elsewhere even when the requested backend already matches. The added same-backend regression failed (HUB became STAFF) before this correction. Ordinary switches still evaluate inventory before economy: an added SHADOW_MIGRATION regression with unavailable economy storage initially failed by allowing the inventory-owner mismatch, then passed after retaining original short-circuit ordering. All 25 focused security-event tests now pass, including seven added routing/conflict/disconnect regressions. No store mutation/API or migration changes. Full clean aggregate checks and exact new-head hosted gates remain pending until the frozen checkpoint is validated. Known unavailable orchestration/private gates are not repeated.

Status PARTIAL / ACTIONABLE_CONTINUATION. Preserve automatic entry vanish, secure names completion, snapshots and existing permission/mode policies. Missing incident correlation and private/provider/Bedrock/Folia/staging acceptance remain. No merge, production action, inventory restoration or backup access. Publish status to the existing docs-only PR #14 without merging.


## Owner spectator hotfix verification, 2026-10-06

Frozen product head 7df1e35c99c6b88e299ee26419ee3882c06b9ded adapts canonical f70baee2 on owner 4597790f. The all-file three-way patch failed atomically because command composition/entry tests differ; no user content was replaced. Applied the compatible self-listing/presentation repair and adapted controls to existing owner command composition instead. /staff tab show|hide and /staff togglevanish delegate to existing VanishCommand, retaining its permission, player/rank and operational-mode gates; the previous two-argument StaffModeCommand constructor remains compatible. Ordinary entry/recovery/exit routes and automatic entry vanish are unchanged. Actor self entry stays listed; other audiences retain their existing policy. Existing recovered player-name/vanish/inventory fixes and corrected runtime harness are preserved. Canonical remembered-entry preference/other integration features are excluded.

Focused Staff/visibility/command tests plus three executable delegation/permission tests pass. Exact frozen-head Java 25.0.3 release-21 clean test/check/runtimeJars passes: 1,707 tests, zero failures/errors, two existing Windows symlink skips; 26 tasks executed, 30 cached, one up-to-date. Docker integration-tests:test explicitly excluded locally; the earlier 4597790f hosted integration pass is historical, not new-head evidence. Wiki 41 pages, whitespace, runtime ZIP CRC and checked RoseChat API exclusion pass. Existing EARS/state helpers are absent; bounded requirements/tasks/evidence remain here. Known unchanged orchestration/private gates were not repeated.

Version 0.1.0-staff-bugs-test.13 is unmerged/local test only. SHA256 Paper bf75734539647808ca7e5d7880454d395e07f957ca15a110dfefca10a563f439; Velocity 880d75d5485d94dbd6bc58ec595a7bbaedcaec5ecf538f4b61463e44c1e235f0; AuthorityBridge ef29e551cfefee25c80b57830c0bdfbfddb4e1649162012cdf59d30b75447ddd; StaffBot 3d52d105477f9618b812b56fbe4994a8d769173fb058d0e173bd6ed3e2abf9f4. Root aggregate builds these modules; no external component pin/dependency/schema/provider interface changes. Full scoped review covers self/other audiences, command permission/mode delegation, constructor compatibility and untouched snapshot/restoration fencing.

Status PARTIAL. Next push to existing PR #1 and inspect exact new-head CI/client proof and review findings; publish updated status through existing docs-only PR #14 without merging. No current-head hosted/client pass yet. Missing inventory/TEST correlation and private/provider/Bedrock/Folia/staging acceptance remain. No merge, production upload/restart, permission change, authority activation, inventory restoration or backup access.

## Owner spectator control hotfix continuation, 2026-10-06

Start owner 4597790fa7c696cce53ffd5de57b846d5e3ee1fa; fork main 711f0fce unchanged. Canonical main f70baee2960510a1b58ba8018f81f0eeed7604fe adds relevant spectator control/self PlayerInfo hotfix. Prior exact-head Coverage, Wiki, Sentinel artifact and four-runtime client proof pass; Codacy upload/draft review/Pi cancellation remain skipped. No new PR findings. Unrelated notification, role-shadow and punishment-menu changes remain excluded.

Requirement SP-01: preserve the actor's own PlayerInfo listing during vanish/audience refresh, without changing other viewers' visibility; SP-02: clickable spectator tab and vanish controls must invoke registered Staff command routes with existing permission/mode checks. Source evidence: periodic presentation explicitly unlists self and audience reconciliation lacks a self-list guard; this is source evidence, not production/client incident attribution. Incorporate only the six canonical hotfix files with a three-way patch, preserving owner automatic vanish entry, player-name completion, mode policies, operation fencing and backend-local snapshots. Validate focused command/staff/visibility tests, clean all-module test/check/runtimeJars, proof compile/runtime CI, Wiki, artifacts and reviewed diff. EARS/state helpers absent; this bounded requirement/task/evidence is the fallback. No schema/provider contract/dependency pin change.

Status PARTIAL / ACTIONABLE_CONTINUATION. Missing inventory/TEST incident inputs and broader private/provider/Bedrock/Folia/staging acceptance remain open. No merge, deployment, authority activation, permission change, inventory restoration or backup access.

## Owner runtime proof repair checkpoint, 2026-10-06

Frozen proof/workflow head 2ebc9521dab5db8591c439a80a439a1a23bdc95e; production executable remains exactly 480c6388258d9c30448c100f28d8f49eb8d5f1af. Corrected Admin/Founder selected-mode expectations and added explicit vanished Spectator selection/return states for both ranks. Vanished wall/floor/ceiling geometry now runs after the Admin explicitly chooses Spectator. Adventure rejection must preserve Creative. Existing snapshot/tool identity, visible geometry, four runtime versions and no-FAIL gates remain. Static single-quoted client regexes now escape literal pipes once rather than twice, preventing unintended ERE alternatives and strengthening client-mode assertions. No product mode-policy change.

Local proof sources compile with Java 25 / release 21, -Xlint:all -Werror and the actual Gradle-resolved Paper compile classpath. Node client syntax, 15 static predicate positive/unrelated/wrong-mode checks, Wiki 41-page validation and whitespace checks pass. Preliminary ad-hoc cache-wide classpaths failed on unrelated manifest paths/old annotation versions; temporary classpath export attempts failed on Gradle configuration-cache/task scope before correction. Those failed setup attempts are not product passes. Local Maven, full YAML parser and Bash interpreter are unavailable; direct javac was used and hosted workflow parsing/Bash execution remain unverified until the rerun. No local server/client matrix result is claimed. Existing 1,702-test product evidence remains historical for unchanged product 480c6388.

Prior exact head 1ce53dec hosted Coverage 37411046544, Wiki 37411046490 and Sentinel artifact 37411046542 passed. Runtime proof 37411046376 failed actual acceptance with preserved artifact 11389665825; never relabel it as an infrastructure failure or pass. New-head hosted runtime proof must execute the corrected assertions. Status PARTIAL; next inspect that rerun and any real residual findings. Previously unavailable unrelated/private/provider gates and inventory/TEST incident correlation remain open. Canonical main 8dd1de34 notification self-test is unrelated and excluded. Source and status PRs stay unmerged; production and backups untouched.

## Owner runtime harness reconciliation, 2026-10-06

Start owner 1ce53dec5741bcd1d1905425ac04f09b9c2c0c47; fork main 711f0fce unchanged. Canonical main 8dd1de344402c4496a5d2826205e83f66e2f4b80 adds Discord notification self-test #338, outside this staff bug slice; inspected and excluded. Exact owner-head Coverage 37411046544, Wiki 37411046490 and Sentinel artifact 37411046542 pass. Staff state reset runtime proof 37411046376 fails acceptance after executing four server/client runtimes; this is a failed executable result, not unavailable infrastructure.

Confirmed harness contract mismatch: it requires Admin/Founder vanish to force Spectator, while the owner-approved Oct 3 contract and VanishGameModePolicy retain their permitted Survival/Creative/Spectator selections. Leaf evidence shows selected Creative/Survival, snapshotSame=true and toolSame=true; these are valid selections. The vanished wall phase also ran in Survival rather than explicitly selecting Spectator. Preserve the failed run as red evidence. Requirement RH-01: assert actual selected permitted modes both vanished and visible; reject Adventure without changing the preceding permitted mode; explicitly select Spectator for vanished geometry then return to Survival; retain four-runtime, client-mode, geometry, snapshot/tool identity and no-FAIL acceptance assertions. Correct proof and workflow only; no production behavior, permission, persistence, provider or API changes. Compile proof against supported API, validate workflow/syntax and push the same PR for an actual rerun. No EARS/state helpers exist; this bounded spec/task/evidence is the fallback.

Status PARTIAL / ACTIONABLE_CONTINUATION. Missing incident correlation, private/staging/provider/client limits and inventory preservation remain unchanged. No merge, production action, backup access or authority activation.

## Owner split-packet verification, 2026-10-06

Frozen product head 480c6388258d9c30448c100f28d8f49eb8d5f1af ports only canonical #337 adapter/test changes (f1615b23 / 912ed7a6) after requirement checkpoint 1ca3ea6d. Current canonical main 672fa3d0 was inspected; fork target 711f0fce is contained. Existing unrelated canonical integration/authority changes remain excluded. Full scoped diff review found no owner restoration, inventory, visibility, completion, permission, persistence or provider-contract changes. No production activation or incident attribution.

Focused tester, Staff Mode, visibility and command tests passed. Frozen-head Java 25.0.3 (release 21) clean test/check/runtimeJars passed: 1,702 tests, zero failures/errors, two existing Windows symlink skips; Gradle reports 26 executed, 30 cached and one up-to-date task. Docker-dependent integration-tests:test was explicitly excluded. Initial unquoted PowerShell property invocation failed task selection before product tasks; rerunning with the quoted property passed. Wiki validation passes 41 pages; diff whitespace and all four runtime ZIP CRC checks pass. Paper contains no checked RoseChat provider API classes. Downloaded ProtocolLib 5.4.0 (SHA256 ee2e7ab9b5386f2d103081c4d108e61b1035df2ca692b53d6e2409fb1f5caccf) exposes USE_ENTITY and lacks ATTACK, so the legacy fallback remains compatible; this local artifact inspection does not establish the currently loaded companion or Minecraft 26.x client acceptance. Three added tests cover dedicated attack, split interaction without legacy reads and legacy action parsing.

Local unmerged test version 0.1.0-staff-bugs-test.12. Paper SHA256 ee0ab427bfa6e57604670981d51f38a3412ccfd8e95186563811356377a5334f; Velocity 880d75d5485d94dbd6bc58ec595a7bbaedcaec5ecf538f4b61463e44c1e235f0; AuthorityBridge d8598ab00bf71b1e22b5289df20e72c83b55c723a246fc1c284b84659830290a; StaffBot 3d52d105477f9618b812b56fbe4994a8d769173fb058d0e173bd6ed3e2abf9f4. Root aggregate owns these builds; no external component pin applies to this two-file adapter slice.

Status remains PARTIAL. Existing EARS/state helpers are absent. Known unchanged orchestration findings and unavailable Docker/MariaDB, hosted/static, staging and real Java/Bedrock/Folia/client gates were not retried or called passed. PR #1 had two resolved threads, no new comments, a CodeRabbit success representing skipped/limited review and no Actions runs at the preceding head. Next push this reviewed checkpoint, inspect exact new-head hosted/review state and continue inventory/TEST correlation when the missing incident inputs arrive. Docs-only follow-up preserves frozen product evidence under validation policy; status PR publication remains unmerged. No product or status merge, production upload, permission change, inventory restoration or backup access.

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

2026-10-05 owner extension: continue the same PR #322 with automatic fresh-entry vanish and player-name completion (IT-09..10). Current main `ba6dcabc9a731e7e3e21c8405778764abdc626f4` includes merged cross-server PR #321 and is normally incorporated as `ebff2f09`; previous assumptions that #321 is unmerged are historical. Preserve transfer/recovery visibility and existing exit policy. No new migration/provider API; validation remains pending for the new product head.

Last updated: 2026-09-22

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Explicit owner assignment — 2026-10-04

2026-10-05 owner extension: streamline the same package's menu workflow in PR #322. Add fixed grouped Staff Dashboard slots, player investigation shortcuts, permission-aware command help, and separate close/confirmed exit controls. No new ownership/handoff persistence, schema or companion API is introduced by this GUI slice. EARS/state helpers remain unavailable; IT-06..08 and regression evidence are recorded in the existing package. Other packages are preserved.

`OWNER-INVESTIGATION-TOOLS` is active in isolated `EnthusiaStaff-investigation-tools` on `package/owner-investigation-tools`, base `18d4f4b05af94ee325842c68d482feedabe5d27f`. See its package record and handoff for patrol/activity/flags/join-alert/slot scope. Existing user checkout and other open package work remain preserved. No merge/deployment authorization is implied.

## Current routing

Owner override, 2026-10-03: this fork session is assigned only to player-name
punishment confirmation in `FainNeito/EnthusiaStaff`, on
`package/owner-punish-confirm-player`. See `work-packages/owner-punish-confirm-player.md`
and its 2026-10-03 handoff. Draft fork PR #1 is `REVIEW` at product head
`b22ce0077f4059a73de847dd09ecc84862c88a44`; local unit/build verification passed
and hosted/integration acceptance remains unverified. The upstream routing table below is preserved.

| Field | Value |
| --- | --- |
| Universal package active state | `ES-X04 — EnthusiaCommend reputation provider` is `COMPLETE`; `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED`; `ES-X03` is `PARTIAL` / `ACTIONABLE_CONTINUATION` while bounded paired static remediation and exact-head validation continue. |
| X01 current classification | `BLOCKED` / `PARKED_BLOCKED` after a 2026-08-26 `ACTIONABLE_CONTINUATION`. The historical repository-resolution blocker materially changed, but the verified provider license now blocks the canonical public aggregate-copy/parity requirement. |
| X01 standalone | Verified `wsg138/Enthusia-RoseChat`, default `master`, reconciliation head `8fcca5420b0f54207d6efa332327b9fd18edb8d8`. GitHub identifies it as a public fork of `BadgersMC/Enthusia-RoseChat`, sourced from `Rosewood-Development/RoseChat`. No provider `AGENTS.md` was present in the verified source tree. |
| X01 license boundary | The checked-in Rosewood Development `LICENSE` permits use/copy/modify/merge while expressly excluding publication and (re)distribution rights. `wsg138/EnthusiaStaff` is public, while `BRANCH-AND-MIRROR-POLICY.md` requires the aggregate component directory to reproduce the standalone source tree for parity excluding only `.git` and aggregate-only `COMPONENT-METADATA.md`. No durable repository evidence currently grants the redistribution right required for that public second copy. |
| X01 implementation state | No provider or Staff implementation branch/PR was created, no RoseChat source was imported, and no product code/test/migration/runtime configuration changed. The worker stopped at the verified license/import gate rather than creating unmergeable or unauthorized work. |
| X01 exact unblock | Obtain a durable, verifiable license change or authorization permitting the required public aggregate copy, or an explicitly authorized canonical package/mirror-policy redesign that removes republication while retaining deterministic supported-source verification. Then reconcile live heads again and perform the normal two-PR implementation, exact-head validation, normal merges, and synchronization/parity process. |
| X03 state | `PARTIAL` / `ACTIONABLE_CONTINUATION`; Staff's response-routing and zero-argument MarketCase-completion repairs have exact-head local, Coverage, artifact, and durable Sentinel evidence. Acceptance remains blocked by Codacy static analysis and canonical Pi. |
| X03 standalone | Market PR #7 is OPEN/DRAFT/CLEAN on `package/es-x03-market-static-remediation` at `81b14c3`; unpaired Market PR #6 is preserved. |
| X03 aggregate | Staff PR #139 is OPEN/non-draft/UNSTABLE on `package/es-x03-market-provider` at `f673281`; current `main` is `4ffab62` and is not merged merely to refresh the unmerged implementation branch. |
| X03 hosted | Market runs `35601165548`/`35601165577`, Staff Coverage `35733465364`, Sentinel artifact `35733465456`, and durable Sentinel job `516` PASS on `f673281`. |
| X03 static/review | Exact Staff Codacy check `106765372218` is `ACTION_REQUIRED` with 1,141 reported issues; no live review threads; CodeRabbit is skipped/manual. |
| X03 Pi/Sentinel | Pi `35733463593` failed pre-dispatch on HTTP 401; exact-head Sentinel restart job `516` reached `PAPER_RESTART_OK`. |
| X03 migrations | Main V20; X03 V21; D09 V22; hunks disjoint. |
| X03 unblock | Continue small paired fixes only for validated findings and have the staging owner repair the bridge credential; then freeze and rerun exact-head gates. |
| X04 standalone | `wsg138/EnthusiaCommend` PR #12 merged normally as `b4a1b57ba918f10ab28d140f9fc0e588a95389c1` after exact reviewed head `325c304512187f274463c31f1649efe0ae56ab7d`. Its fully contained package branch remains safe to delete when an authorized branch-deletion path is available. |
| X04 aggregate | Staff PR #152 merged normally as `e91fc1150a82cc0df081a82bb3dd69714f8bfc14` after exact reviewed head `7ef4b70ed01ad46b925669a0b1378053d8e26789`; the temporary Staff package branch is deleted. |
| X04 product state | Transactional/versioned reputation moderation and the Staff sanction projection are implemented and merged. Post-merge standalone↔aggregate shared Git objects are identical; aggregate-only `COMPONENT-METADATA.md` is the one allowed extra file and records `IN_SYNC`. |
| X04 review state | Commend PR #12's six correctness/data-integrity threads are resolved; Staff PR #152 has zero live inline threads. Exact-head Codacy checks passed with no issues. CodeRabbit status was successful but explicitly skipped automated review, so no automated full-review approval is claimed. |
| X04 standalone validation | Exact-head workflow `32797266212` / job `97651014296` PASS on `325c304...`: Temurin Java 21, Maven `clean verify`, 110 tests with zero failures/errors/skips, PMD, artifact `9545261529` / `sha256:14704bdc74a6ae261226b098e4488dd75ff12152e06b2e962122ce04a153d9bb`, and Codacy with zero annotations. |
| X04 Staff hosted validation | Exact-head Coverage/full validation `32882926827` / job `97916497081` PASS on `7ef4b70...`: Java 21 full build/tests including MariaDB/Testcontainers; 27 provider API source types / 0 leaks; JaCoCo 50.50% line / 41.12% branch / 52.93% instruction; artifact `9576803249`, digest `sha256:8f9e79118425b19ffdd20b685466039edfbd12f41fb56f68d1a4b17528193d00`; Codacy found no issues. |
| X04 Sentinel | Exact-head Sentinel workflow `32882926734` PASS; durable job `250` reached `PAPER_RESTART_OK`. |
| X04 canonical Pi | Public run `32882924737` and correlated private run `32883859152` / job `97919562717` PASS on trusted `Lincoln-PI-4`, including exact artifact verification, guarded disposable Paper boot/restart, durable sanitized evidence, and cleanup. |
| X04 remaining work | No implementation, validation, synchronization, merge, or canonical-publication work remains. Only deletion of the fully contained standalone package branch is pending because the prior connected mutation surface did not expose branch deletion. |
| Discord program live work | `ES-D09` is `BLOCKED` / `PARKED_BLOCKED`; PR #203 remains preserved open/unmerged at frozen executable head `a48390c50c6968e75437abd2dd05c0faeece355d`. Exact executable validation/static/review evidence is green; merge is blocked by migration serialization because `main` owns through V20, X03 / PR #139 owns branch-local V21, and D09 owns V22. D08 remains `PLANNED`; D10/D12 remain `PLANNED` because D09 is incomplete; D13 remains independently parked. |
| Discord latest completion | `ES-D07 — Discord punishment enforcement` is the latest completed Discord implementation package. |
| D09 implementation state | `BLOCKED` / `PARKED_BLOCKED`. PR #203 remains open/unmerged on `package/es-d09-discord-investigations` at frozen executable/product head `a48390c50c6968e75437abd2dd05c0faeece355d`. |
| D09 executable validation | Exact repair validation `35387277563` / job `105737009460` PASS: Java 21 clean build/tests, MariaDB/Testcontainers integration tests, StaffBot runtime verification, PMD zero valid changed-code findings, changed-method complexity bounds, regression bounds, and `git diff --check`. |
| D09 hosted/static/review | Exact-head Coverage `35388283034` / job `105740323648`, Staff Bot PR Artifact `35388283005`, Staff Bot Configuration Cache `35388283022`, Sentinel Restart Artifact `35388282989`, Codacy Static `105740695905`, Codacy diff coverage, and Codacy coverage variation all PASS. Codacy static has zero annotations / zero new valid findings. CodeRabbit status is successful; all substantive product-code threads are resolved. |
| D09 migration blocker | Live `main` owns Staff migrations through V20; X03 / PR #139 legitimately owns branch-local V21; D09 owns V22. V21 is absent from `main`, so D09 cannot safely merge V22. |
| D09 exact unblock | Merge the legitimate owner of Staff migration V21 into `main`, then reconcile D09 with the resulting live migration chain, resolve only legitimate conflicts, rerun all exact-head executable gates affected by reconciliation, refresh review/Codacy evidence, and only then reconsider merging PR #203. |
| D09 production boundary | No production Discord mutation/data access/configuration, deployment, cutover, LiteBans authority change, AutoMod enforcement, cross-platform authority change, or issue #43 acceptance occurred. |
| D07 implementation state | `COMPLETE`. PR #201 merged normally as `ca949a8531ea39efdf1becccc052b9c59cf30d24`; package base `074c0ae0bee7222bcd5c9096f8db0071f5b84cdf`; frozen executable product `aea6696cb97df4463b90abfcbbd8bfa4bb80b913`; final pre-merge head `e9d8a904c4192c9a4ab5fdfe34df8d2590567a95`. Merge parents are exactly the base and final feature head. |
| D07 executable validation | Review-repair workflow `34925882460` / job `104243730808` PASS: Temurin Java 21.0.12+1, full clean build/tests, `:staff-bot:verifyStaffBotRuntime`, PMD zero findings, changed-Java CCN <= 8, practical method length <= 50, argument count <= 8, bounded worker-test size 431, and `git diff --check`. Frozen executable Codacy check `104245761385` PASS with zero annotations/up-to-standards. All four substantive CodeRabbit findings were repaired with regression coverage and all four threads are resolved. |
| D07 final state-only validation | Exact `aea6696...` → `e9d8a904...` delta is only three `ai-agents` Markdown tracking files. Exact final-head Coverage `34926790460`, Staff Bot PR Artifact `34926790540`, Staff Bot Configuration Cache `34926790404`, Sentinel Restart Artifact `34926790424`, and state-only Codacy Static `104247092167` all PASS. No executable input changed. |
| D07 destructive staging | `NOT RUN / unavailable`, not passed. No authorized non-production D07 destructive target/fixture/harness was found; production Discord was not used as a substitute and no production Discord action/config/data mutation occurred. |
| D07 merge/containment | Merge `ca949a8531ea39efdf1becccc052b9c59cf30d24` exactly contains final feature head `e9d8a904...`; post-merge comparison is one merge commit with zero file differences. `package/es-d07-discord-punishment-enforcement` is absent. |
| D07 temporary tooling | `tmp/es-d07-codacy-inspect-20260914` contains only disposable D07 inspection/repair scripts/workflow state and no unique product work; it is safe to delete. The connected GitHub mutation surface exposes no branch-delete action. |
| D07 downstream routing | D09 is now `BLOCKED` / `PARKED_BLOCKED` on V21→V22 migration serialization with #203 preserved. D08 remains `PLANNED` because its separate live proof that current Minecraft moderation services can accept integration without changing production authority is not established. D10/D12 remain `PLANNED` because D09 is incomplete. D13 remains parked. |
| D13 implementation state | Product implementation is complete enough for acceptance testing on PR #178 / `package/es-d13-role-sync-replacement`; frozen head `92b207d67a1098acc2dcfbddd35ac56e01711f95`, observed base/main `e7b338979c3824687147a0b3253638324571a3a7`. Six concrete CodeRabbit findings were repaired with regression coverage and all visible review threads are resolved. Production ENFORCE remains rejected. |
| D13 hosted/static validation | Exact frozen head: Coverage/full validation `34893756317` / job `104142632033` PASS; 52.52% line / 42.70% branch / 54.75% instruction coverage; 27 provider API source types / 0 leaks; Staff Bot PR Artifact `34893756524` PASS; Staff Bot Configuration Cache `34893756377` PASS; Sentinel Restart Artifact `34893756520` / job `104142890224` PASS; Codacy Static `104143227910` PASS with zero annotations/new valid findings; Codacy Diff Coverage `104145475928` PASS at 55.93% with no repository gate defined. |
| D13 canonical Pi | **NOT PASS / private runtime NOT RUN.** Public exact-head run `34893930914` built successfully, but bridge job `104146199543` failed before private dispatch during `Revalidate exact candidate and supersede stale staging` because the staging workflow-history request returned HTTP `401 Bad credentials`. No Pi/Paper/database runtime ran; transient transfer cleanup succeeded. Do not rerun the identical path without evidence the cross-repository credential/authorization condition changed. |
| D13 Sentinel | **NOT PASS / durable result unavailable.** Exact-head restart and status requests were submitted; artifact build is green, but no durable `PAPER_RESTART_OK` is visible. No repeated identical request is enqueued. |
| D13 original parity gate | **NOT RUN / unavailable.** Original package contract requires staging parity with legacy DiscordSRV role sync. Authorized non-production state exposes neither authoritative legacy Minecraft-group→Discord-role/effective managed-role state nor staging StaffBot D13 role-sync runtime configuration/parity harness. Unit tests, hosted CI, Sentinel artifact creation, or ordinary Pi boot do not substitute for parity. |
| D13 exact unblock | Provide authorized non-production legacy DiscordSRV mapping/effective managed-role state and staging StaffBot D13 runtime configuration; keep legacy role sync enabled; run D13 in SHADOW across current linked identities; require zero unexplained managed-role drift; retain sanitized evidence; then reconcile/revalidate before merge. Production cutover remains separately gated. |
| D13 production boundary | No production DiscordSRV disablement, production Discord configuration/data access, production role-sync enforcement, LiteBans authority change, issue #43 acceptance, deployment, or cutover is authorized or performed. Preserve PR #178 and its implementation branch while parked. |
| D16 product state | `3a79000eaa139ec107118d3fdb05b29e5e52097c` is the owner-accepted UI candidate; `8811294c17532825aeae1d271fe2a3163042ba9c` is the frozen reconciled executable head; `aa32355a0d378ca4c6b03041b80d005df73f6fcd` is the final reviewed/validated pre-merge head; PR #187 merged normally as `848aba7ac6a115dc3723c034b281917d63f1f1bd`. The merged product retains the signed real-data read bridge, channel/player browse, bounded same-channel message/context reads, explicit identity/profile allowlists, workflow/history/case/note/account views, product-language hardening, and simulation-only destructive behavior. |
| D16 owner acceptance | `PASS`: the owner reviewed the moderation UI and stated `The UI looks good.` Acceptance evidence contains no signed launch material, credentials, private message bodies, raw moderation records, backend signatures, or secrets. |
| D16 exact-head validation | Exact final head `aa32355a0d378ca4c6b03041b80d005df73f6fcd`: Coverage `34766165648` / job `103747432981` PASS; validation artifact `10320537834`, digest `sha256:fa9b77ee77fec9e73c140d9cc02685da25c23f600be087ea83663f07279e06c5`; Moderation Web Validation `34766165643` PASS; Staff Bot PR Artifact `34766165650` PASS; Staff Bot Configuration Cache `34766165642` PASS; Sentinel Restart Artifact `34766165664` PASS; Pi Staging Supersession `34766164245` PASS; Codacy Static Code Analysis `103747616510` PASS with zero annotations / zero new valid findings; Codacy Diff Coverage `103748653603` PASS at 52.54%; Codacy Coverage Variation `103748653922` PASS at +0.03% against the -1.0% target. |
| D16 protected staging | Moderation Web Staging Deploy `34766163166` PASS on exact final head `aa32355a...`: fixed private tunnel/origin, Worker deployment, authenticated launch/session, exact-origin CORS, signed direct-read denial/replay behavior, one-time launch replay rejection, simulation-only runtime, and no real player/message query by the synthetic probe all passed. |
| D16 review/analyzer state | All three substantive CodeRabbit correctness threads were fixed and resolved; valid unresolved review-thread count is zero. The final-head automatic CodeRabbit status was skipped for manual review and remains explicit non-pass diagnostic history rather than being called a pass. The later tracking edit that attempted to make a fresh exact-head CodeRabbit response a new blocker is non-authoritative under `VALIDATION-POLICY.md`. Protected staging also reported four high-severity npm audit findings in the Wrangler development dependency graph; D16 and then-current `main` share the identical `moderation-web/package-lock.json` blob `8f1ff002ef318cee4ffb8351adab12d612a5054b`, so this is recorded as pre-existing dependency debt rather than a D16-introduced finding. |
| D16 merge/containment | PR #187 merge `848aba7ac6a115dc3723c034b281917d63f1f1bd` has parents pre-merge `main` `06519c0c5acdcf6276278204201f3c8b20767805` and exact feature head `aa32355a0d378ca4c6b03041b80d005df73f6fcd`. Merge and feature trees are identical at `c5c02a86d4db3861b4d9b7abfc2636323e9d9c12`; post-merge comparison is feature `ahead 0 / behind 1 / files []`, proving exact containment. Live branch search returns no `package/es-d16-moderation-read-bridge`; temporary implementation cleanup is complete. |
| D16 remaining work | No implementation, validation, merge, containment, or implementation-branch cleanup remains. |
| D04 terminal state | `COMPLETE`. Final reviewed/validated head `da0371681f5a44c72a614c8d6637b85d9080291d`; Staff PR #151 merged normally as `4e7621b7a42e812cc7bf806a029f37a753cdd9f3`; exact product tree containment is proven and the temporary D04 branch is absent. |
| D04 exact-head validation | Coverage `33029697612` / job `98379158884` PASS; Codacy static `98379478044` PASS with zero annotations; Codacy diff coverage `98380515790` PASS; Sentinel artifact `33029697604` / job `98379112319` PASS; all visible PR #151 inline review threads resolved. |
| D04 Sentinel/canonical Pi | Durable Sentinel job `292` reached `PAPER_RESTART_OK`. Canonical public Pi `33029762105` and correlated private `33030278019` / job `98380970512` PASS on trusted `Lincoln-PI-4`, including exact bridge/artifact verification, guarded disposable Paper boot/restart, durable sanitized evidence, private/public cleanup, and terminal publication. |
| D04 merge/containment | Merge `4e7621b7a42e812cc7bf806a029f37a753cdd9f3` has exact feature head `da037...` as second parent; both trees are `63c2a0924d38ac9ce8e0a208f0eb79a671af37fc`. Post-merge compare is one ahead/zero behind; `V20__discord_account_linking.sql` is canonical on `main`; `package/es-d04-account-linking` is absent. |
| D05 product state | `COMPLETE`. Frozen reviewed product SHA `5f24ba1818c81e0a30a516fa70c8597586184b00`: isolated Java 21 process, JDA 6.5.0 with no privileged Gateway intents, exact application/guild/channel fencing, bounded work/replay primitives, loopback health/readiness, callback generation fencing, privacy-safe lifecycle logging, deterministic shutdown, shaded runtime verification, tests/docs, and non-destructive `--smoke-test`. Existing webhook delivery remains separate. |
| D05 frozen hosted validation | Frozen product SHA `5f24ba1...`: Coverage/full validation `32874248685` / job `97888464396` PASS; Staff Bot Configuration Cache `32874248800` / job `97888275507` PASS twice with configuration-cache problems treated as failures; Sentinel Restart Artifact `32874248693` PASS; validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`; JaCoCo 50.76% line / 41.41% branch / 53.21% instruction. |
| D05 live Discord acceptance | `PASS`: trusted `wsg138/EnthusiaStaff-Staging` run `32926306691`, attempt 3 / job `98071453002`, on trusted self-hosted `Lincoln-PI-4`. Exact frozen source `5f24ba1818c81e0a30a516fa70c8597586184b00`; staging application `1541279616881397772`, guild `1410303324745371709`, required channel `1541286004298752091` view/send fence, readiness, smoke exit 0, and graceful close/shutdown all passed. No moderation action/test message, Discord configuration change, production-data access, or bot-token exposure occurred. |
| D05 final exact-head validation | Exact final head `936155cc356075aff10fd966de19e3d4bd8ca5f0`: Coverage `33006430216` PASS; Staff Bot Configuration Cache `33006430238` PASS; Sentinel Restart Artifact `33006430207` PASS; all visible inline review threads resolved/outdated. |
| D05 final canonical Pi | Public run `33007222310` PASS through exact-head binding, runtime build, private dispatch, verdict collection, transient-transfer cleanup, and terminal publication. Correlated private run `33008160488` / job `98307232213` PASS on trusted runner ID 2 `Lincoln-PI-4`, including exact bridge verification, guarded disposable Paper boot/restart, durable sanitized evidence, and cleanup. |
| D05 merge/containment | PR #160 merged normally as `7bc8739bdc3f77db23c8b649f8c227f008162e47` with final feature head `936155cc...` as second parent. Post-merge compare is one commit ahead, zero behind, with zero file differences. Temporary branch `package/es-d05-staff-bot-runtime` is absent. |
| D05 remaining work | None. ES-D05 has no implementation, validation, merge, containment, or cleanup work remaining. |
| D06 product state | `COMPLETE`. Frozen reviewed/validated product head `b624ee799aea7db7c561b0b064733374d4c61067` delivers the full read-only Discord staff moderation UX with authoritative linked-staff authorization, exact IPv4 loopback authority binding, ambiguity-safe 25-choice selectors, signed expiring replay-resistant private components, read-time reauthorization, and no destructive moderation side effects. |
| D06 final exact-head validation | Coverage `33204412446` / job `98961747084` PASS; Staff Bot Configuration Cache `33204412468` PASS; Sentinel Restart Artifact `33204412444` / job `98961683122` PASS; hosted Codacy static `98961965089` PASS with zero annotations; Codacy diff coverage `98963786634` PASS at 45.74% with no defined gate; all five visible PR #177 inline review threads resolved/outdated. |
| D06 Sentinel/canonical Pi | Durable Sentinel job `327` reached `PAPER_RESTART_OK`. Canonical public Pi `33204694500` and correlated private `33205431529` / job `98965140421` PASS on trusted `Lincoln-PI-4`, including exact bridge verification, guarded disposable Paper boot/restart, sanitized/durable evidence, transient-transfer cleanup, and terminal publication. Sanitized runtime SHA-256 `728ab454b9cb546625985a02fa5d6c9fc7a6e37020974a409862f411e58dc96b`. |
| D06 merge/containment | PR #177 merged normally as `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3` with exact feature head `b624ee799aea7db7c561b0b064733374d4c61067` as second parent. Merge and feature trees are identical at `5b3fd4d313dd4437dc04c346bd39efcc4e00f007`; post-merge compare is one ahead/zero behind with zero file differences. The implementation branch is absent. |
| D06 remaining work | None. The temporary diagnostic workflow is removed and its retained diagnostic branch has zero file differences from merged `main`; it is safe to delete when a branch-delete mutation is available. No unique D06 work remains. |
| Migration state | Canonical `main` owns D04's forward-only V20. X03 / PR #139 owns branch-local `V21__market_compliance_journal.sql`; parked D09 / PR #203 owns `V22__discord_investigation_state.sql`. V21 is still absent from live `main`, so D09 V22 cannot merge. D16's owner-authorized transition path successfully applied/populated the selected EnthusiaStaff schema during authorized staging. D05/D06/D07/X01/D13 status publication adds no migration/source migration, and D07 implementation itself adds no Flyway migration. |
| Independently parked packages | `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED` on verified license/public-aggregate authorization. `ES-X03` remains `PARTIAL` / `ACTIONABLE_CONTINUATION` for bounded paired remediation and exact-head validation, while acceptance remains blocked by Codacy and canonical Pi; it legitimately owns branch-local Staff V21. `ES-D09` is independently `BLOCKED` / `PARKED_BLOCKED` on migration serialization because V21 is absent from `main` while D09 owns V22. `ES-D13` remains independently `BLOCKED` / `PARKED_BLOCKED` on unavailable original DiscordSRV non-production parity input/runtime. |
| Production boundary | D07 is complete as Discord-only enforcement code. D09 remains unmerged/parked at #203 and performed no production Discord mutation/data/configuration, deployment/cutover, LiteBans authority change, AutoMod enforcement, or issue #43 acceptance. D16 remains read-only/simulation-only; D13 remains unmerged/parked. Credentials, private production data, raw player/message evidence, PM data, and secrets remain excluded from repository/CI/chat evidence. LiteBans remains authoritative. |
| Universal current handoff | `ai-agents/reports/package-handoffs/2026-09-22-es-x03-marketcase-completion-validation.md` |
| Discord current handoff | `ai-agents/reports/package-handoffs/2026-09-19-es-d09-investigations-blocked.md` — terminal `BLOCKED` / `PARKED_BLOCKED` publication. |
| Discord parked handoff | `ai-agents/reports/package-handoffs/2026-09-14-es-d13-role-sync-blocked.md` |

## X01 current parked record

The 2026-08-26 universal worker correctly resumed X01 because the historical repository-resolution condition materially changed: live GitHub now exposes supported `wsg138/Enthusia-RoseChat`, default branch `master`, with accessible source at `8fcca5420b0f54207d6efa332327b9fd18edb8d8`. The repository is a public GitHub fork in the RoseChat fork network, and no repository-specific `AGENTS.md` is present.

License verification then exposed a new hard boundary before implementation. The checked-in Rosewood Development license allows use/copy/modify/merge but expressly excludes publication and (re)distribution. The canonical external-component policy requires publishing the standalone source tree under public `components/enthusia-rosechat/` and proving whole-tree parity after both normal merges. No durable repository evidence currently grants that redistribution right. The worker therefore did not import source or create provider/Staff implementation branches or PRs.

Exact unblock is a durable, verifiable license/authorization change that permits the required public aggregate copy, or an explicitly authorized canonical architecture/policy redesign that removes republication while retaining deterministic supported-source verification. Until then, X01 remains parked and product build/review/static/Sentinel/Pi/staging results are not claimed because no product implementation head exists. The state-only publication is validated only under the repository's documentation/orchestration applicability rules.

## X03 current parked record

The paired continuation preserves Market PR #7 at
5b6606c2f71a410ed6f369b0b893a7888638a7f2 and Staff PR #139 at
e67a67585179b7a8dd6b6dc8c81c9fe567f04ef1. Exact component parity is true
at shared hash
6ba7be19e647b9093bb9670b79026585eb5306f83e66480894fed3912b1f96f7.
The owner-authorized repair corrects Bedrock SELL/BUY submissions that passed
a zero cost override and failed the existing positive-cost invariant before
persistence. It uses the existing price fallback; TRADE behavior, existing
shops, balances, migrations, and Java menus are unchanged.

Market build 35474189763 and Wiki 35474189668 passed. Staff Coverage
35474547939 passed at the exact paired head. V20, V21, V22, and shared-file
ownership remain unchanged and disjoint.

X03 remains parked on two independent non-passing gates. Codacy is
ACTION_REQUIRED with 1,129 findings that include Markdownlint, Lizard,
immutable-migration RAC-table, and dependency-coordinate secret-pattern
reports. Pi run 35474189686 failed before private dispatch on HTTP 401 Bad
credentials; no private runtime ran. Durable Sentinel restart is NOT RUN.

Preserve PR #139, Market PR #7, and unpaired Market PR #6. An authorized
path-scoped Codacy decision and repaired Pi workflow-history authentication
are required before a fresh exact-head gate run. Only terminal green required
gates permit normal merges; post-merge standalone-to-aggregate parity remains
mandatory. Current handoff:
ai-agents/reports/package-handoffs/2026-09-18-es-x03-parked-static-and-pi.md.

## X04 completed record

X04 completed through normally merged Commend PR #12 and Staff PR #152 after exact-head Java 21, test, static, review, Sentinel, and canonical Pi gates. Post-merge containment and standalone↔aggregate shared Git objects are identical, and component metadata records `IN_SYNC`.

The only residual cleanup is the fully contained standalone package branch. It has no unique work and is safe to delete when an authorized branch-deletion path is available; it is not an implementation or validation blocker.

## D09 current parked record

D09 implementation PR #203 remains open/unmerged at exact frozen executable/product head `a48390c50c6968e75437abd2dd05c0faeece355d` on `package/es-d09-discord-investigations`.

Exact repair validation `35387277563` / job `105737009460` passed the full Java 21/MariaDB/Testcontainers/StaffBot runtime path, PMD, changed-method complexity, regression bounds, and `git diff --check`. Exact frozen-head Coverage `35388283034` / job `105740323648`, Staff Bot PR Artifact `35388283005`, Staff Bot Configuration Cache `35388283022`, Sentinel Restart Artifact `35388282989`, Codacy Static `105740695905`, diff coverage, and coverage variation all succeeded. Codacy static has zero annotations / zero new valid findings. CodeRabbit is successful and every substantive product-code review thread is resolved.

The package cannot merge because the Staff Flyway chain is serialized. Live `main` contains migrations through V20; X03 / PR #139 legitimately owns branch-local V21; D09 owns V22. V21 remains absent from `main`. D09 must not renumber V22 or absorb X03.

Exact unblock: merge the legitimate owner of Staff migration V21 into `main`, then reconcile D09 with the resulting live migration chain, resolve only legitimate conflicts, rerun all exact-head executable gates affected by reconciliation, refresh review/Codacy evidence, and only then reconsider merging PR #203.

No production Discord mutation/data/configuration, deployment, cutover, LiteBans authority change, AutoMod enforcement, cross-platform authority change, or issue #43 acceptance was performed. Canonical handoff: `ai-agents/reports/package-handoffs/2026-09-19-es-d09-investigations-blocked.md`.

## D13 current parked record

D13 implementation PR #178 remains open/unmerged at exact frozen candidate `92b207d67a1098acc2dcfbddd35ac56e01711f95` on `package/es-d13-role-sync-replacement`.

Product/hosted CI is green. Codacy is green with zero new valid static findings and 55.93% diff coverage under no defined diff gate. All six concrete CodeRabbit findings were repaired/resolved.

Canonical Pi is not a runtime pass: public run `34893930914` built the exact candidate but bridge job `104146199543` failed before private dispatch on a staging workflow-history `401 Bad credentials`; no private Pi/Paper/database runtime ran. Sentinel artifact generation is green, but no durable `PAPER_RESTART_OK` is visible after the exact-head restart/status requests, so Sentinel is not a pass.

The original package-required DiscordSRV staging parity is `NOT RUN`: authorized non-production legacy managed-role mapping/effective state and staging StaffBot D13 role-sync runtime/parity configuration are unavailable. Exact unblock is to provide those inputs/runtime, leave legacy role sync enabled, run D13 in SHADOW across current linked identities, require zero unexplained managed-role drift, retain sanitized evidence, and then reconcile/revalidate before any merge.

Production/cutover remains unauthorized/unperformed. Preserve PR #178 and its branch. Canonical handoff: `ai-agents/reports/package-handoffs/2026-09-14-es-d13-role-sync-blocked.md`.

## D07 completed record

D07 completed through Staff PR #201 after the frozen executable product `aea6696cb97df4463b90abfcbbd8bfa4bb80b913` passed the final repair gate and the state-only final head `e9d8a904c4192c9a4ab5fdfe34df8d2590567a95` passed its applicable hosted/static/review gates.

Review-repair workflow `34925882460` / job `104243730808` passed Temurin Java 21.0.12+1, full clean build/tests, `:staff-bot:verifyStaffBotRuntime`, PMD zero findings, changed-Java CCN/method-length/argument bounds, bounded worker-test size 431, and `git diff --check`. Frozen executable Codacy `104245761385` passed with zero annotations. Four substantive CodeRabbit findings were repaired with regression coverage and all four threads are resolved.

The exact frozen-product→final-head delta changes only three `ai-agents` Markdown tracking files. Exact final-head Coverage `34926790460`, Staff Bot PR Artifact `34926790540`, Staff Bot Configuration Cache `34926790404`, Sentinel Restart Artifact `34926790424`, and state-only Codacy `104247092167` all passed. Destructive Discord staging remains truthfully `NOT RUN / unavailable` because no authorized non-production D07 destructive harness exists.

PR #201 merged normally as `ca949a8531ea39efdf1becccc052b9c59cf30d24` with exact expected parents. Post-merge compare has zero file differences; the implementation branch is absent. Temporary inspection branch `tmp/es-d07-codacy-inspect-20260914` has only disposable tooling and no unique product work, but this connected GitHub surface has no branch-delete mutation.

ES-D07 is `COMPLETE`. D09 is routed `READY` for a future worker but is not started; D08 remains `PLANNED` pending its separate Minecraft integration-readiness proof; D12 remains `PLANNED`; D13 remains parked. This worker stops after D07 terminal publication.

## D16 completed record

D16 completed through Staff PR #187 after owner UI acceptance, normal moving-main reconciliation, fresh exact-head hosted/static/protected-staging validation, and zero valid unresolved review threads.

Owner-accepted candidate `3a79000eaa139ec107118d3fdb05b29e5e52097c` was reconciled with current `main` into executable `8811294c17532825aeae1d271fe2a3163042ba9c`; final documentation/checkpoint head `aa32355a0d378ca4c6b03041b80d005df73f6fcd` retained the same executable state and passed Coverage `34766165648`, web validation `34766165643`, StaffBot artifact/configuration-cache `34766165650`/`34766165642`, Sentinel `34766165664`, Pi supersession `34766164245`, protected staging `34766163166`, Codacy static `103747616510`, diff coverage `103748653603`, and coverage variation `103748653922`.

PR #187 merged normally as `848aba7ac6a115dc3723c034b281917d63f1f1bd`. Exact feature containment is proven: merge and feature trees are identical at `c5c02a86d4db3861b4d9b7abfc2636323e9d9c12`, post-merge comparison has no file delta, and the temporary implementation branch is absent.

ES-D16 is `COMPLETE`. ES-D07 is also `COMPLETE`; ES-D09 is dependency-complete `READY` but not active; ES-D08 remains `PLANNED` pending its separate readiness proof; ES-D13 is `BLOCKED` / `PARKED_BLOCKED`. This worker does not activate another package.

## D06 completed record

D06 completed through Staff PR #177 after every required exact-head gate passed on frozen product head `b624ee799aea7db7c561b0b064733374d4c61067`.

Coverage `33204412446` / job `98961747084`, configuration-cache `33204412468`, Sentinel artifact `33204412444` / job `98961683122`, hosted Codacy static `98961965089`, durable Sentinel job `327`, and canonical public/private Pi `33204694500` -> `33205431529` / job `98965140421` all passed. All five visible inline review threads were resolved/outdated before merge.

PR #177 merged normally as `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3`. Exact containment is one commit ahead, zero behind, and zero file differences; merge and product tree are `5b3fd4d313dd4437dc04c346bd39efcc4e00f007`. The temporary implementation branch is absent. The diagnostic-only workflow was removed from the retained diagnostic branch, which now has zero file difference from merged `main` and no unique work.

ES-D06 and ES-D07 are `COMPLETE`; ES-D09 is dependency-complete `READY` for a future worker but is not active; ES-D13 is `BLOCKED` / `PARKED_BLOCKED`. This worker does not activate another package.

## D05 completed record

D05 completed through Staff PR #160 after the owner-authorized real Discord smoke, fresh exact-head hosted validation, and canonical Pi staging all passed.

Frozen product source `5f24ba1818c81e0a30a516fa70c8597586184b00` retains the real Discord acceptance from staging run `32926306691` attempt 3 / job `98071453002`. Final synchronized/reviewed head `936155cc356075aff10fd966de19e3d4bd8ca5f0` passed Coverage `33006430216`, configuration-cache `33006430238`, Sentinel artifact `33006430207`, and public/private canonical Pi `33007222310` -> `33008160488` / job `98307232213`.

PR #160 merged normally as `7bc8739bdc3f77db23c8b649f8c227f008162e47`. Exact containment is one commit ahead, zero behind, and zero file differences; the temporary D05 branch is absent. D05 added no migration and did not absorb D04/X03/production work.

ES-D05, ES-D06, and ES-D07 are `COMPLETE`; ES-D09 is dependency-complete `READY` for a future worker but is not active; D13 is `BLOCKED` / `PARKED_BLOCKED`. This worker does not activate another package.

## D04 completed record

D04 completed through Staff PR #151 after exact-head Java 21/MariaDB/Testcontainers, Codacy, review, independent Sentinel restart, and canonical public/private Pi gates all passed.

Exact final product head `da0371681f5a44c72a614c8d6637b85d9080291d` passed Coverage `33029697612` / job `98379158884`, Codacy static `98379478044`, Codacy diff coverage `98380515790`, Sentinel artifact `33029697604` / job `98379112319`, durable Sentinel job `292` with `PAPER_RESTART_OK`, and canonical public/private Pi `33029762105` -> `33030278019` / job `98380970512` on trusted `Lincoln-PI-4`.

PR #151 merged normally as `4e7621b7a42e812cc7bf806a029f37a753cdd9f3`. Its tree `63c2a0924d38ac9ce8e0a208f0eb79a671af37fc` is identical to the exact validated feature tree, so containment is exact. `V20__discord_account_linking.sql` is canonical on `main`, and the temporary D04 branch is absent.

No production import, Discord production change, private-data access, deployment, LiteBans authority change, issue #43 acceptance, or cutover occurred.

## Independent ES-X03 routing

D04 serialization is no longer the X03 blocker. Market #7 at `81b14c3` and
Staff #139 at `f673281` have exact component parity at
`e7082c5bb1aacbcd95ac8457aa17392a740c3fe5df6154e743eebb4bc6019839`.
Fresh Market hosted checks, Staff Coverage, Sentinel artifact, and durable
Sentinel restart passed for the exact heads. Codacy remains `ACTION_REQUIRED`,
and canonical Pi stopped before private dispatch because its bridge credential
was rejected. Follow the active X03 handoff; do not replace either
implementation branch, absorb standalone Market PR #6, bypass the bridge, or
treat missing private runtime as a pass.

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

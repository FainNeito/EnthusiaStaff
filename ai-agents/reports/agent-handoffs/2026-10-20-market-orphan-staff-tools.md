# Market acceptance follow-up: orphan Staff tools

## Scope and routing

The owner requested a Roundtable continuation of the remaining Market work from Codex
session `01a11918-66d6-75b0-ac6c-5399eebc1b00`. The coordinator assigned the bounded
Staff prerequisite investigation as Roundtable `MARKET-02`, task
`01a15971-3951-7c28-b479-442dd8d04119`. This record covers that repair only; it does
not replace the aggregate package registry, start another historical package, or
claim completion of the Market acceptance campaign.

- Authoritative repository: `wsg138/EnthusiaStaff`.
- Starting main: `c945b92e088fe9d1fc326284d53df47799a6d128`.
- Isolated branch: `package/market-02-orphan-staff-tools`.
- Related existing PR: #476, inspected at
  `c898a2621a5b2ebaff7cba11d5049228518c1e21`. Its detached-session ownership and
  retry-ticket changes do not remove orphan tools in the no-session or verified
  local-exit paths. Its changes remain separate.
- Production access, deployment, inventory reset, permission changes, and client
  mode changes are outside this repair. No such operation was performed by this
  worker.

## Spec

1. When a successful durable lookup returns no Staff session, recovery shall
   remove only items carrying this plugin's `staff_tool` string tag from the
   player's inventory and cursor, on the player's entity scheduler, before
   releasing the recovery fence.
2. While storage is unavailable, the lookup fails, or recovery belongs to an
   older connection/attempt, recovery shall not interpret the uncertainty as
   permission to remove tools or release a newer fence.
3. When a saved snapshot has been restored, verified, and durably closed,
   cleanup shall retire ephemeral Staff tools that were already embedded in the
   saved inventory. It shall preserve the original saved bytes and checksum and
   every ordinary item, including vanilla items of the same material.
4. When a later active or pending local session replaces the closing session,
   the older cleanup callback shall preserve the newer session and its tools.
5. If cleanup cannot execute after closure, the local state shall remain
   retryable. A subsequent recovery shall read the durable absence of an active
   session and shall not replay an already closed snapshot.

## Prove

Source inspection established two connected paths in the starting main:

- `recover` cleared its gate and returned after `store.active` returned empty,
  leaving Staff-tagged inventory items intact. The tool dispatcher then denied
  the tagged item's interaction because no current session token existed.
- Fresh entry captured the existing inventory unchanged. Exit removed current
  tools before restoring the saved inventory; a snapshot already containing
  orphan tools therefore reintroduced them.

The regression invokes the actual manager against synthetic store, player,
inventory, and queued worker/entity schedulers. It is not a source-text test.
The post-restoration tests enter at the verified-checksum completion boundary;
they test closure and cleanup ordering, not Paper's item serialization or the
full player-state codec. No private player snapshot is used.

The first baseline run reached JUnit but failed in the test adapter because
Paper's `Plugin` interface also exposes `Namespaced.namespace()`. That run is
invalid as product-red evidence. The adapter was corrected before rerunning.

The corrected baseline run completed on Java 25.0.3 at
`2026-10-20T13:31:49.597Z`: 10 manager tests, 4 failures, 0 errors, 0 skips,
11.6 seconds. The four failures demonstrated the orphan inventory/cursor,
premature inactive-fence release, contaminated verified snapshot, and failed
cleanup retry defects. The other six safety cases passed. This is the valid
behavior-red checkpoint; later candidate tests add the independent review
regressions without replacing that evidence.

## Engine and architecture

- Keep the no-session transition fence until entity-owned cleanup completes.
- Give each recovery attempt an identity invalidated by retry or clear, so an
  old callback cannot claim a replacement attempt. Acquire and complete the
  expected attempt atomically with the bounded player cleanup; invoke external
  exit listeners only after the fence has been released.
- Wait for `completeExit` to succeed before cleaning a restored inventory, and
  retain the exact closing local session until the entity callback has verified
  it. A rejected or throwing scheduler leaves it retryable, and a later durable
  absence read cleans that known closed session without replaying its snapshot.
- Remove Staff-tagged cursor items using the same existing plugin tag predicate.
- Preserve the public `JavaPlugin` constructor signatures. Package-local
  `Plugin` constructor overloads permit behavioral testing with real scheduling
  boundaries and no server bootstrap.

No database schema, snapshot format, historical migration, command, permission,
tool authorization, or visual menu changed. Tagged items remain unusable outside
their active session; this repair does not bypass that authorization check.

## Refine and validation status

- `git diff --check`: passed locally.
- Corrected Java 25 baseline: 10 tests, 4 behavior failures, 0 errors or skips.
- Candidate focused execution: Java 25.0.3, 21 tests passed (15 manager and 6
  recovery-gate tests), 0 failures, 0 errors, 0 skips; report timestamp
  `2026-10-20T13:46:10Z`, 36.4 seconds. The coordinator applied the exact reviewed
  production patch to a fresh isolated checkout of the starting main.
- Independent production and focused-test review: no remaining actionable
  findings on patch SHA-256
  `94d40167924758927cd7fc06ba0d68abcf0aa78858991c2aa09808242f7865e0`.
  The independent reviewer identified and verified fixes for throwing entity
  schedulers, non-atomic check/clear races, global callbacks removing replacement
  state, and exit-listener reentrancy. The reviewed candidate has 15 manager
  scenarios and 6 recovery-gate tests.
- Existing restoration/audit source-marker tests now follow the new completion
  helpers while retaining durable-closure, exact-session, cleanup, and success
  notification ordering assertions. Their test-only patch SHA-256 is
  `c08384f0ddfc932a930dc9e185a3df287882143fb7e9ad728d36ddd993196dbb`.
  Candidate production and the 21 focused tests are byte-identical to the
  independently reviewed patch. Independent review also found no actionable
  findings in this test-only delta.
- Staff package lifecycle group: 273 tests passed, 0 failures, 0 errors, 0 skips,
  Java 25.0.3, 15.2 seconds; JUnit timestamps
  `2026-10-20T13:47:01Z`–`2026-10-20T13:47:02Z`. This total includes the 21
  focused manager/gate tests. The run used both reviewed patches. Project
  compilation enables `-Xlint:all` and `-Werror`.
- Commands executed by the coordinator from the isolated Windows candidate:

  ```text
  gradlew.bat --no-daemon :paper:test --tests net.enthusia.staff.paper.staff.StaffOrphanToolRecoveryTest --tests net.enthusia.staff.paper.staff.StaffModeRecoveryGateTest
  gradlew.bat --no-daemon :paper:test --tests net.enthusia.staff.paper.staff.*
  ```
- Current TEST artifact provenance and current durable mode state remain
  unverified. The observed client symptom and the source regression do not prove
  that this candidate has run on those clients.
- Full repository, hosted CI, staging, Java/Bedrock/Folia runtime acceptance,
  merge, and deployment: not performed by this worker.

The coordinator inspected the installed Minecraft development skill and found
no separate project SPEAR/EARS validator or state helper. This bounded
requirement/task/evidence record follows spec → prove → engine → architecture →
refine without claiming an unavailable helper passed.

## Exact next action

Publish the reviewed local branch as a draft repair PR, preserving existing
PR #476 and unrelated package state. Complete applicable full repository and
hosted checks before any merge. Map the TEST Staff artifact to its source before
attributing the live symptom or planning separately authorized TEST activation.
The branch is a reviewed, source-tested candidate; it does not claim Market
client acceptance, a merged repair, or runtime activation.

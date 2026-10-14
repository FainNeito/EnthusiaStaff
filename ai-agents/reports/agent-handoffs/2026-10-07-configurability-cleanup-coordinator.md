# EnthusiaStaff coordinator handoff — configurability, cleanup, and acceptance

Prepared: 2026-10-07. Live GitHub is authoritative; inspect current `main`, open PRs, review threads, and checks before acting because active Policy v2 and Discord workers are moving the repository quickly.

## 1. Identity

- Repository: `wsg138/EnthusiaStaff`
- Current main at handoff freeze: `444c44a6f3868c66f5eff605d36c099cc8c0da06`
- Owner direction: EnthusiaStaff is a **live production plugin**, not a globally pre-release project.
- Current coordinator programs:
  - #425 — Configurability vNext: versioned configuration and safe reload migration
  - #426 — Repository cleanup and production-status documentation refresh
  - #436 — Configurability C1: centralized messages.yml foundation
- Current C1 branch: `config/c1-messages-foundation`
- Current C1 PR: #449 — https://github.com/wsg138/EnthusiaStaff/pull/449
- Current C1 head at handoff freeze: `06c2d32b7761b7ac834b36d20445bfb865eee94f`
- PR #449 is intentionally still draft while hosted checks/review settle.

## 2. Owner-approved direction

The owner wants EnthusiaStaff to become broadly configurable without turning the work into a one-shot rewrite.

The canonical active plan is `docs/current-roadmap.md`.

The intended configuration migration is:

1. C0 — shared versioned configuration/validation/reload foundation;
2. C1 — centralized messages;
3. C2 — ranks/capabilities;
4. C3 — Staff Mode profiles/tools;
5. C4 — Policy v2 punishment configuration integration;
6. C5 — punishment GUI presentation;
7. C6 — remaining GUIs/integrations.

Important principles:

- shipped defaults reproduce current production behavior;
- one invalid candidate must not partially activate;
- immutable snapshots are validated before atomic publication;
- schema versions and stable IDs are required;
- policy and presentation stay separate;
- authorization rechecks, transaction/idempotency guarantees, privacy fences, audit integrity, safe concurrency, recovery, and other security/correctness invariants remain code-owned;
- Policy v2 remains the single punishment-policy foundation; do not create a competing punishment engine.

The long-term target includes separate files such as `messages.yml`, `ranks.yml`, `staff-mode.yml`, integration settings, and separate punishment policy/menu presentation files. The exact final layout may evolve.

## 3. Work merged during this coordinator run

### Helper no-vanish

PR #419 was merged earlier in this coordinator run as `28a966033308659043ab9da24b0add349e941237`.

Behavior now in main:

- Helper no longer receives vanish through the rank policy;
- Mod+ retains vanish;
- Helper cannot use the Staff Mode vanish tool;
- runtime eligibility, saved state, transfers, async persistence, and rank-race boundaries force Helper visible rather than trusting a stale/direct permission.

Do not recreate this as a separate feature. Future C2/C3 work should represent the same policy through configurable capabilities.

### Post-launch roadmap and documentation status

PR #427 merged the current roadmap/status correction at merge `6d9f5ba4`.

PR #429 merged the second current-status pass at `529cdbf8e48d92657f3977562011cd6f28c7a98b`.

Current-facing docs now correctly say EnthusiaStaff is live. Historical LiteBans/pre-release wording is intentionally retained in dated ADRs, handoffs, legacy wiki snapshots, and migration evidence where it describes the state at the time.

### C0 configuration foundation

PR #428 — `Add read-only versioned configuration validation` — is merged.

- PR head before merge: `547f943ec37808e214a9fc32dd823e87286cbf1f`
- merge commit: `e41062728131fafa93c7eb639435607ce0fc8e53`

C0 added/reconciled:

- `/estaff config validate` as a read-only validation surface;
- version reporting for the registered versioned configuration families;
- `/estaff config reload` as an alias for the existing reload coordinator, not a second reload engine;
- reuse of the existing `enthusiastaff.reload` authorization boundary;
- `docs/configuration.md`;
- tests proving validation does not mutate runtime state.

The C0 audit also established that the repository already had substantial safe-reload infrastructure: typed snapshots, path-aware validation, restart-required rejection, transaction/rollback coordination, atomic report configuration, and Policy v2 versioned parsing. Do not replace that architecture.

## 4. C1 messages.yml — current implementation

C1 is tracked by #436 and implemented in draft PR #449.

Current exact branch head at freeze:

`06c2d32b7761b7ac834b36d20445bfb865eee94f`

The branch is reconciled with current main `444c44a6`.

Implemented:

- shipped `paper/src/main/resources/messages.yml`;
- explicit `schema-version: 1`;
- typed `MessageKey` registry;
- strict `MessageConfigurationLoader`;
- required-key validation;
- unknown-key rejection;
- duplicate-YAML-key rejection;
- exact placeholder-contract validation;
- literal/escaped placeholder insertion;
- immutable `MessageConfigurationSnapshot`;
- atomic active snapshot publication;
- reload wrapper that validates first and publishes only after the existing delegated reload succeeds;
- failed message validation leaves the previous known-good catalog active;
- failed delegated reload leaves the previous message catalog active;
- shared `/estaff config validate` now includes `messages.yml`;
- the first bounded migration covers static operator-facing `/estaff` responses;
- unrelated runtime-generated error details/logging remain code-owned for now;
- concurrent-reader regression coverage verifies readers observe complete immutable snapshots.

MiniMessage boundary:

- C1 supports restricted MiniMessage formatting for message templates;
- allowed intent is ordinary colors, text decorations, and reset;
- interactive/data tags such as click, hover, insertion, selector, NBT, keybind, etc. are rejected during validation;
- placeholder values are escaped so player/operator data cannot become interactive MiniMessage markup;
- existing string-based `StaffMessageStyle` behavior for unrelated commands was preserved after hosted tests caught an Adventure component-shape regression.

Important hosted-test history:

- an older PR #449 head failed two StaffChat component-equality tests because a generic styling helper wrapped string messages in a parent component;
- local commit `12fe2c33` repaired that without weakening C1 formatting;
- the exact previously failing `StaffChatCommandTest` and `StaffChatRosePermissionTest` now pass locally along with the C1 tests.

Focused tests passing on current main/C1 integration include:

- `StaffChatCommandTest`
- `StaffChatRosePermissionTest`
- `EstaffCommandReloadTest`
- `MessageConfigurationLoaderTest`
- `AtomicMessageConfigurationTest`
- `MessageConfigurationReloadActionTest`
- `VersionedConfigurationValidatorTest`
- `PolicyV2OwnerSnapshotTest`

Validation location: PR #449. Inspect the live checks for exact current-head Coverage, runtime proof, Sentinel, Codacy, and review state before merging.

Do not merge #449 merely because this handoff says the focused suite passed.

## 5. Repository cleanup program

Tracker: #426.

Completed so far:

- README/current docs say the project is live under active development;
- current feature reference no longer says LiteBans is production authority;
- old LiteBans migration/cutover pages are clearly marked historical where appropriate;
- current database-recovery wording was refreshed;
- roadmap and cleanup audit are linked from README.

Important initial audit finding: several things that look like clutter are **not yet safe deletions**.

Do not blindly delete:

- `ai-agents/` — referenced by current workflows/docs/orchestration;
- `WORKSPACE-MANIFEST.md` — referenced by current goals/docs/orchestration validation;
- `reports/PROJECT-COMPLETION-AUDIT.md` — referenced by agent/audit material;
- Codacy diagnostic workflows such as `w15-codacy-diagnostic.yml` and `dump-codacy-annotations.yml` — they had recent runs and need dependency/use review before removal;
- bundled component source merely because it makes the repository visually large.

Next cleanup work should build a reference map, improve navigation/archive boundaries, and delete only proven-dead material in a dedicated PR.

## 6. Active work owned by other workers — do not overlap

### Policy v2

At handoff freeze, open PR #452 is:

`Policy v2: add versioned typed remedy enforcement bindings`

Branch: `policy-v2/remedy-binding-metadata`.

Current main also contains owner-approved Policy v2 publication work through `444c44a6`.

Do not start C4/punishment-policy implementation or edit active Policy v2 internals without reconciling live GitHub and the other worker.

### Discord / DiscordSRV replacement

Separate workers own the Discord replacement/cutover surfaces.

Open work includes #422, #340, #339, #214, #203 and related migration tasks.

The owner explicitly told this coordinator not to take over the DiscordSRV replacement. Do not create a parallel bot/transport/cutover implementation.

## 7. Remaining production/hands-on acceptance blockers

These are existing workstreams, not new feature ideas.

### #350 — real Spectator noclip reliability

Still OPEN.

The narrow Polar Spectator mitigation and merged diagnostics are already in main. Diagnostics can report actual gamemode, rank identity, eligibility, Polar check/mitigation type, cancellation decision, and snapshot age.

Hands-on acceptance still needs a current candidate deployed to SMP and repeated real Spectator phasing through wall/floor/ceiling, including failure reproduction. Capture the diagnostic lines when phasing fails.

Do not fake noclip and do not globally grant `polar.bypass`.

### #392 — HUB ProtocolLib ATTACK warning

Still OPEN.

The code fix is already merged. Acceptance still needs a fresh HUB startup log proving the unregistered ATTACK warning is gone after the fixed artifact is actually running.

Do not close on code evidence alone.

### #395 — Helper projectile/firework pass-through

Still OPEN.

Hands-on test:

- active Helper in Survival;
- fire a firework/projectile through them;
- it should continue through without premature detonation/removal or Helper interference.

If it passes, record production evidence and close #395.

### #343 — Guild Strikes / LumaGuilds

Still OPEN.

Upstream `BadgersMC/LumaGuilds#209` remains OPEN and mergeable.

- title: `Restore Guild Strikes via EnthusiaStaff lifecycle feed`
- head: `62cca6da5126660d363e77e677b43430e0991e85`

Wait for the upstream owner merge. Do not force-merge BadgersMC/LumaGuilds.

After upstream merge, obtain/deploy the resulting LumaGuilds build and verify punishment -> Guild Strike -> revoke/expire lifecycle behavior.

## 8. Deployment boundary

The configurability/cleanup work described above has **not** itself been deployed by this coordinator.

Do not treat the previously delivered Helper-no-vanish JAR as a current-main artifact anymore; main has advanced substantially since then.

Before production acceptance/deployment:

- reconcile exact live main/PR head;
- build/use an exact-head artifact;
- preserve rollback;
- distinguish Paper-only changes from any Velocity/network counterpart requirements;
- do not infer deployment success from CI alone.

## 9. Recommended continuation order

1. Inspect live PR #449 at exact current head.
2. Resolve any current-head Codacy/review/test findings.
3. Reconcile #449 with live main if main moved in a materially relevant way.
4. When #449 is genuinely green/review-ready, switch it out of draft and merge normally.
5. After C1 foundation lands, migrate message families in bounded PRs rather than one giant rewrite. Recommended order:
   - Staff Mode + vanish;
   - reports;
   - punishment/sanction user-facing messages;
   - investigations/inventory;
   - tester tools/integrations.
6. Keep #425 as the parent configurability tracker.
7. Do not begin C4 work while active Policy v2 workers own that surface.
8. Continue #426 cleanup with reference mapping/navigation first, destructive deletion later.
9. Advance hands-on acceptance #350/#392/#395 when a current candidate is deployed.
10. Watch LumaGuilds #209 and finish #343 after upstream merge.
11. Freeze a broader release candidate and run the canonical hands-on acceptance checklist (#236) after the active feature/config branches settle.

## 10. Boundaries preserved

- No credentials are stored here.
- No raw production/player data is stored here.
- No production deployment is claimed by this handoff.
- No DiscordSRV replacement work is claimed by this coordinator.
- No Policy v2 ownership is claimed by this coordinator.
- No upstream LumaGuilds merge is authorized here.
- Historical evidence is preserved rather than rewritten to match current state.
- Live GitHub is authoritative over every SHA/status recorded above.

## 11. Immediate next command-level checks for the next coordinator

Before changing code:

- fetch `origin/main`;
- inspect open PRs;
- inspect PR #449 head/checks/review threads;
- compare #449 against live main;
- read #425, #426, #436 and `docs/current-roadmap.md`;
- do not assume the SHAs in this handoff remain current.

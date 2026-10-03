# Owner-directed fork change: confirm punishment by player name

The owner explicitly requested this change on 2026-10-03 in the newly created
`FainNeito/EnthusiaStaff` fork. This is the assigned work item for this session;
upstream package selection, component imports, and production work are outside
its scope. No upstream package is renumbered or reassigned.

- Base: `0aaefc4a22415f6af7778887cdab7d4b6f40468a`.
- Branch: `package/owner-punish-confirm-player`.
- Status: `REVIEW`; local implementation verified, draft fork PR [#1](https://github.com/FainNeito/EnthusiaStaff/pull/1) open. Merge acceptance is pending.
- Product head: `b22ce0077f4059a73de847dd09ecc84862c88a44`.
- Change: `/punish confirm <player>` resolves the current directory name and the
  sender's single unexpired stored draft for that target. Original draft IDs work.
- Persistence: existing V9 unique actor/target constraint already replaces older
  drafts, so no new table, migration, unbounded query, or in-memory session is needed.
- Safety: the selected ID goes through the original actor-bound lookup, duty,
  hierarchy, recommendation, approval, and idempotent application checks. A replaced
  or consumed draft cannot silently switch to another draft inside the same call.
- UX: confirmation prompts and online name completion use player names; unnamed
  targets retain the draft-ID fallback. Offline known names use the existing directory.
- Tests: name/Bedrock/UUID/missing lookup routing and actor/target isolation,
  consumed draft, and expiry regressions; existing confirmation suites also run.
- Production: no deployment, permission change, player punishment, or database access.
- Handoff: `ai-agents/reports/package-handoffs/2026-10-03-owner-punish-confirm-player.md`.

Local validation: Java 25.0.3 clean unit build/check/runtimeJars with
`:integration-tests:test` explicitly excluded; 1,554 tests, zero failures/errors,
two existing Windows symlink tests skipped. All 41 Wiki pages validate and
`git diff --check` passes. The final Paper tests/build also pass. Paper runtime
entrypoint and test version were verified in the JAR.

Paper artifact: `EnthusiaStaff-Paper-0.1.0-confirm-player-test.1.jar`, SHA-256
`702b4b1857949aa1a023f5adb5132e5432fe434c301dd17995bac44e1a82d1b2`.

Remaining gates: Docker/MariaDB integration,
hosted static/review, and applicable staging/live acceptance remain unverified.
No merge-readiness or production acceptance is claimed.

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

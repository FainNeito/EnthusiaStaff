# Confirm-player fork handoff

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

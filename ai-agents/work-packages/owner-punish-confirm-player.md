# Owner-directed fork change: confirm punishment by player name

The owner explicitly requested this change on 2026-10-03 in the newly created
`FainNeito/EnthusiaStaff` fork. This is the assigned work item for this session;
upstream package selection, component imports, and production work are outside
its scope. No upstream package is renumbered or reassigned.

- Base: `0aaefc4a22415f6af7778887cdab7d4b6f40468a`.
- Branch: `package/owner-punish-confirm-player`.
- Status: local implementation verified; fork PR delivery in progress. Merge acceptance is pending.
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

Remaining gates: push the branch and open the fork PR; Docker/MariaDB integration,
hosted static/review, and applicable staging/live acceptance remain unverified.
No merge-readiness or production acceptance is claimed.

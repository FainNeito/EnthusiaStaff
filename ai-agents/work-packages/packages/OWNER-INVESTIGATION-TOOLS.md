# OWNER-INVESTIGATION-TOOLS — Player investigation tools

## GUI cleanup verification, 2026-10-05

Owner extended the same package with GUI streamlining; implementation PR #322 remains draft/open/unmerged at frozen product head `85d4db419ee6dcf0266a3cf686a89fb5b96c1e9e`, based on freshly verified main `18d4f4b05af94ee325842c68d482feedabe5d27f`. Fixed grouped Staff Dashboard tools, player investigation shortcuts, permission-aware help, loading Back, and separate Close/confirmed Exit are implemented. Existing command/service checks remain authoritative; no schema/provider API was added by this GUI slice. Delayed delivery checks the original inventory, active session, inspector/action permissions and visibility. Other owner/transfer/Discord package work is preserved.

Manual requirements IT-06..08: permission filtering retains fixed slots and prevents hidden routing; Close keeps Staff Mode; Exit requires confirmation; player shortcuts expose only permitted existing workflows; delayed actions recheck authority/visibility; evidence save and punishment confirmation stay explicit; help shows authorized shortcuts. Two new fixed-slot/filter assertions failed on previous code, then all eight routing/view tests passed. No EARS/state helpers exist.

Java 25.0.3 clean test/check/runtimeJars passes for the frozen product tree: 1,629 discovered, 1,627 passed, two existing Windows symlink skips, zero failures/errors. Wiki passes 41 pages; four runtime JAR CRCs pass; 31 contract source paths checked with zero provider leaks. Local artifacts use `0.1.0-investigation-gui-local-test` and are unmerged test artifacts. Paper SHA-256 `419b628063e9c0729ca1b0297694fa5711d6ac841eb25aa9fa4ef73f580d1451`.

Status: PARTIAL / ACTIONABLE_CONTINUATION. Exact-head Codacy Static Code Analysis check 111616091447 succeeds with zero annotations; live inline review comments are empty. Coverage 37263635829, Sentinel artifact 37263635877, Wiki 37263635847 and state-reset proof 37263635842 stop at ACTION_REQUIRED for maintainer approval of fork workflows; none proves hosted product execution. Pi supersession 37263633748 is skipped, not a pass. MariaDB integration was excluded (prior Docker unavailability), staging/runtime providers and Java/Bedrock usability remain unverified. Existing unchanged orchestration baseline remains 460 findings, not a pass. Proposed V24 migration sequencing across pending V22/V23 remains unresolved. Next: address real exact-head findings, obtain missing hosted/database/runtime evidence and reviewed delivery. Unified timeline, durable ownership/handoffs, follow-up inbox and evidence bundles remain future work.

This docs-only status delta does not change product code or rerun product gates; executable results belong to the frozen product head. It supersedes older current-head summaries below while retaining historical evidence. No merge, deployment, restart, production or player-data change is authorized. Canonical status publication remains unfinished until docs PR #323 reaches main.


## Assignment and routing

Owner instruction: 2026-10-04, “Work on that”, approving the LuxStaff review recommendations.
Internal COMP-STAFF package; authoritative base `18d4f4b05af94ee325842c68d482feedabe5d27f`.
Branch `package/owner-investigation-tools`; isolated checkout `EnthusiaStaff-investigation-tools`.
Status: PARTIAL / ACTIONABLE_CONTINUATION. One aggregate PR; no merge or production authorization.
Existing user checkout and its untracked artifact are preserved.

## Spec and acceptance boundaries

- IT-01: WHEN patrol succeeds, subsequent patrols SHALL prefer eligible players not recently visited by that actor; failures SHALL NOT consume visits. History SHALL be bounded and cleared on quit. Existing target and actor guards remain mandatory.
- IT-02: WHEN accepted player interaction events occur, the inspector SHALL show their latest local-session timestamps only to viewers with `enthusiastaff.inspect.activity`. Session records SHALL be bounded and removed on quit; no cheating inference is made.
- IT-03: Staff SHALL create and resolve configurable investigation flags with separate view/edit permissions, actor, reason, expiry and optional verified target case. Durable creation and resolution audit SHALL commit atomically. Flags never impose sanctions.
- IT-04: Authorized viewers SHALL receive bounded join notifications for active flags/recent notes, with visibility checked at delivery and an inspect shortcut. No note text is broadcast.
- IT-05: Operators SHALL configure unique hotbar slots for supported tool IDs without weakening session/owner/material/slot validation. Existing command shortcuts retain normal authorization.

## Prove, engine, architecture, refine

Existing patrol can repeat a player immediately and has no successful-visit history.
Open PR #215 already repairs stale target revalidation; reuse its three source/test files at `b0ab306568081fc4afe602db36ddd98355e7affa`, with explicit attribution and no claim it is merged.
PR #321 changes transfer/vanish ownership; do not duplicate those changes.
Use pure domain state/ports, JDBC infrastructure, Paper events and entity schedulers, existing bounded workers.
No project-local EARS validator/state helper was found; this requirement/task/evidence record is maintained manually.

## Migration reconciliation

Live main has V21 vanish selected mode; open #316 and #203 both use V22; #279 uses V23.
New flag schema uses V24. This is an unmerged proposal: migration ordering must be reconciled across those PRs before release; never modify deployed migration history or enable out-of-order as a shortcut.

## Tasks and evidence

- [x] Fetch/inspect authoritative main, dirty checkout, open PR overlaps and migrations.
- [x] Implement and prove patrol/activity/slot behavior.
- [x] Implement flags and join alerts; MariaDB failure/upgrade suite compiles but local runtime proof is unavailable.
- [x] Update user-facing documentation and canonical routing/handoff.
- [ ] Build/tests, provider boundaries, diff review, exact-head CI/static/review.
- [x] Publish draft aggregate [PR #322](https://github.com/wsg138/EnthusiaStaff/pull/322). Staging/live player acceptance remains separate.

Completion requires normal reviewed merge and applicable validation. Merge/deployment remain owner-controlled.

## Current evidence

Product head `bee3307a3d59b4c5cae6abe411c1f01514c45fae`, draft PR #322: clean unit/build proof (1,624 passed, two skipped), four JAR integrity/API checks, Wiki and Codacy zero findings. Hosted fork workflows, MariaDB/staging and migration ordering remain pending; status PR #323 records exact evidence. No merge/deployment authorization.

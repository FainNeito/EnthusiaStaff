# OWNER-INVESTIGATION-TOOLS — Player investigation tools

## Assignment and routing

Owner instruction: 2026-10-04, “Work on that”, approving the LuxStaff review recommendations.
Internal COMP-STAFF package; authoritative base `18d4f4b05af94ee325842c68d482feedabe5d27f`.
Branch `package/owner-investigation-tools`; isolated checkout `EnthusiaStaff-investigation-tools`.
Status: ACTIVE / ACTIONABLE_CONTINUATION. One aggregate PR; no merge or production authorization.
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
- [ ] Implement and prove patrol/activity/slot behavior.
- [ ] Implement flags and join alerts with persistence/failure evidence.
- [ ] Update user-facing documentation and canonical routing/handoff.
- [ ] Build/tests, provider boundaries, diff review, exact-head CI/static/review.
- [ ] Publish reviewable aggregate PR. Staging/live player acceptance remains separate.

Completion requires normal reviewed merge and applicable validation. Merge/deployment remain owner-controlled.

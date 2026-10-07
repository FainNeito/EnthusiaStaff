# EnthusiaStaff Current Roadmap

_Last reconciled against `main` at `61c8a25f` on 2026-10-07._

## Project status

EnthusiaStaff is a **live production plugin on the Enthusia network** and continues to receive active development. The repository contains both deployed functionality and work that is still staged, shadowed, pending provider integration, or awaiting hands-on acceptance.

Do not describe the entire project as "pre-release." Feature-specific documentation should instead state whether that feature is live, staged, shadow-only, pending acceptance, or planned.

Historical handoffs, migration records, ADRs, and old validation reports may intentionally describe older pre-release/LiteBans states. Preserve those as historical evidence rather than rewriting them to match today's deployment.

## Near-term completion work

These existing workstreams remain part of the current completion plan and should be finished rather than duplicated:

- **#350 Spectator noclip reliability** — use the merged Polar/Spectator diagnostics to reproduce the intermittent phasing failure and fix the real cause.
- **#392 ProtocolLib fake-entity compatibility** — verify the HUB startup warning is gone with a fresh runtime log and close when production evidence is clean.
- **#395 Helper projectile/firework pass-through** — complete hands-on acceptance of the modern Paper projectile path.
- **#343 Guild Strikes** — finish the LumaGuilds provider migration once the upstream LumaGuilds PR is merged, then verify issue/revoke/expire behavior live.
- **#271 Staff Mode Authority v2** — finish the Mod/Developer capability model, active-duty authority boundaries, lower-rank audit behavior, and remaining recovery/acceptance work.
- **#324 Moderation notifications** — polish punishment DMs and linked Minecraft punishment notifications after the authoritative punishment surfaces stabilize.
- **#347 AuthorityBridge retirement** — remove the standalone bridge after the Paper-native authority endpoint is confirmed in production.
- **#236 hands-on acceptance** — freeze a candidate and systematically test the production-facing behavior that automated CI cannot prove.
- **#264/#268 DiscordSRV retirement** — continue in the dedicated Discord workstream; do not build a competing transport/cutover path here.
- **#355 Policy v2** — continue the existing versioned policy/shadow/cutover program; do not create a second punishment-policy engine.

## Configurability program

Tracking issue: **#425 — Configurability vNext: versioned configuration and safe reload migration**.

### Goal

Move owner/operator policy and presentation choices out of Java and into validated, versioned configuration while keeping correctness/security invariants in code.

The migration must preserve today's behavior by default. A subsystem is not considered migrated merely because values can be read from YAML; its validation, reload behavior, compatibility, tests, and operational documentation must also be complete.

### Design principles

1. **Current behavior is the compatibility baseline.** The first config-backed version of a subsystem should behave like current production unless the owner explicitly changes policy.
2. **One canonical configuration snapshot.** Load the complete candidate tree, validate all files and cross-references, then atomically replace the active immutable snapshot.
3. **Reject partial reloads.** One invalid file or unresolved reference rejects the candidate configuration and leaves the previous known-good snapshot active.
4. **Version every schema.** Config files need explicit versions and documented migration/compatibility behavior.
5. **Stable IDs over display strings.** Permissions, capabilities, punishment reasons, categories, sanctions, GUI actions, and integrations use stable identifiers.
6. **Separate policy from presentation.** Moving a GUI icon cannot change sanction logic; changing message wording cannot change authorization.
7. **Security invariants remain code-owned.** Config must not disable authorization rechecks, idempotency, transaction boundaries, sensitive-data filtering, secret redaction, audit integrity, safe concurrency, or public/private data fences.
8. **Bound every operator-controlled value.** Durations, menu sizes, retry counts, weights, thresholds, text sizes, and collections need safe validation.
9. **Reloads are observable.** Operators should be able to validate, reload, inspect active config revision, and see a concise diff/diagnostic result.
10. **Policy v2 is the punishment-policy foundation.** Do not create a competing punishment config model. Extend/integrate the Policy v2 versioned policy configuration.

### Target configuration shape

Exact filenames may evolve, but the intended separation is:

```text
plugins/EnthusiaStaff/
  config.yml
  messages.yml
  ranks.yml
  staff-mode.yml
  integrations.yml

  punishments/
    policy.yml
    categories.yml
    history.yml
    remedies.yml
    menu.yml

  guis/
    reports.yml
    staff-tools.yml
    punish.yml
```

Generated/runtime state does not belong in operator policy files.

### Phase C0 — configuration core

Build the shared foundation before migrating large subsystems:

- schema/version metadata;
- typed immutable runtime models;
- path-aware validation errors;
- cross-file reference validation;
- safe defaults and compatibility behavior;
- atomic snapshot publication;
- reload lifecycle and failure handling;
- active revision/hash/status reporting;
- `/estaff config validate`;
- `/estaff config reload`;
- `/estaff config status`;
- optionally a bounded `/estaff config diff` that reports meaningful policy changes without exposing secrets;
- tests for invalid YAML, unknown fields where appropriate, cycles, unresolved IDs, bounds, duplicate IDs, partial reload failure, concurrent readers, and restart/reload behavior.

No feature semantics should change in C0.

### Phase C1 — messages

Create a centralized `messages.yml` for normal player/staff-facing text:

- command success/failure responses;
- Staff Mode and vanish messages;
- punishment/reports/freeze/inventory messages;
- common validation errors;
- configurable MiniMessage formatting;
- documented placeholders with validation;
- fallback/default messages if a key is missing during an upgrade.

Do not move security-sensitive logging/audit wording into editable public messages when that would weaken diagnostics.

### Phase C2 — ranks and capabilities

Replace scattered rank-specific policy checks with a typed capability model.

Examples include:

- Staff Mode entry;
- vanish;
- reports;
- freeze;
- spectate;
- inventory view/edit;
- punishment issue/review;
- cheat tester/fake-base controls;
- staff tools;
- sensitive history;
- recovery/admin actions.

`ranks.yml` should define rank inheritance/capability assignment without making Java depend on literal LuckPerms group names.

Java should answer questions such as "does this actor currently have VANISH capability?" rather than repeatedly branching on `HELPER`, `MOD`, etc.

The active-duty/session requirement remains a separate authorization condition from permanent rank identity.

### Phase C3 — Staff Mode profiles and tools

Move operator policy for Staff Mode into validated configuration:

- allowed profiles/gamemodes by rank/capability;
- tool availability;
- tool/menu layout where safe;
- vanish entry/default behavior;
- patrol/spectate options;
- interaction restrictions that are policy choices;
- cooldowns/bounds;
- audit categories and presentation settings.

Hard safety rules that prevent item duplication, stale authorization, unsafe snapshot restore, or unaudited destructive mutations remain code-owned.

### Phase C4 — Policy v2 configuration integration

Use the existing Policy v2 model for:

- categories;
- offense definitions;
- offense-specific questions/attributes;
- sanction ladders and durations;
- related-history relationships;
- weights;
- adaptive/continuous decay parameters;
- remedies/compliance restrictions;
- escalation thresholds;
- discretion/review bounds;
- Admin/Founder review requirements;
- public-safe projection labels.

Unknown/unconfigured conduct must continue to use the explicit Policy Gap/review path rather than allowing ordinary staff to invent arbitrary sanctions.

### Phase C5 — punishment GUI presentation

Make the punishment workflow presentation configurable independently from policy:

- category order;
- slots;
- materials/icons;
- names/lore;
- pagination;
- review-screen presentation;
- reason grouping;
- allowed display metadata.

A GUI edit must never silently redefine punishment policy.

### Phase C6 — remaining GUIs and integrations

After the core surfaces are stable, migrate:

- reports GUI;
- Staff Tools GUI;
- inspector presentation;
- integration toggles/routes that are genuinely operator policy;
- Discord destination/presentation settings;
- optional-provider behavior;
- other bounded feature settings currently hard-coded.

### Configurability acceptance

Before calling the program complete:

- current production behavior can be represented entirely by the shipped default configs for migrated policy surfaces;
- invalid config cannot partially activate;
- reload cannot corrupt active Staff Mode/punishment/report/inventory/recovery state;
- all IDs/references validate;
- schemas are versioned;
- restart-required settings are explicit;
- configuration changes are auditable;
- tests cover compatibility and failure paths;
- docs explain each file and provide safe examples;
- no security/correctness invariant became an operator toggle.

## Repository cleanup program

Tracking issue: **#426 — Repository cleanup and production-status documentation refresh**.

The repository has accumulated implementation-era coordination material and stale status language. Cleanup should improve navigability without deleting evidence that is still operationally or historically useful.

### Goals

- make the repository root small and obvious;
- clearly separate current operator/developer documentation from historical agent/handoff evidence;
- remove or archive superseded generated reports and worker artifacts after confirming nothing still consumes them;
- retire obsolete workflow/config scaffolding only after proving it is unused;
- stop presenting historical migration assumptions as current production status;
- keep durable ADRs, migrations, audit evidence, and relevant historical handoffs;
- avoid mixing bundled component source with Staff docs in ways that make ownership unclear;
- add a clear documentation index and "current vs historical" boundary;
- reconcile open GitHub issues whose acceptance language no longer matches reality.

### Cleanup order

1. **Status correction first** — README/current docs say the plugin is live and identify feature-specific pending work instead of calling the entire project pre-release.
2. **Inventory the root and docs tree** — classify each file/area as runtime source, active docs, active tooling, generated evidence, historical handoff, or obsolete candidate.
3. **Archive before delete** — move still-useful historical coordination material under an obvious archive/history location where practical.
4. **Delete only proven dead artifacts** — confirm no workflow, test, script, doc link, or active issue consumes them.
5. **Consolidate duplicate status documents** — prefer one current roadmap/status entry point and make historical snapshots clearly dated.
6. **Clean workflows** — remove obsolete diagnostic/manual workflows only after checking current CI dependencies.
7. **Reconcile GitHub issues/PRs** — close or supersede stale trackers; update old acceptance gates that refer to removed systems such as LiteBans where the historical wording is no longer the current operating truth.
8. **Final navigation pass** — README should tell a new contributor/operator where to find current architecture, configuration, operations, roadmap, testing, and historical material.

## Work sequencing

Do not stop current high-value feature work just to perform a cosmetic cleanup.

Recommended order:

1. Finish current runtime acceptance blockers (#350, #392, #395, #343).
2. Start C0 configuration core in parallel where it does not collide with Policy v2/Discord work.
3. Migrate messages (C1).
4. Align Staff Mode Authority v2 with configurable capabilities (C2/C3).
5. Let Policy v2 provide the punishment configuration foundation (C4).
6. Add configurable punishment GUI presentation (C5).
7. Continue other GUIs/integrations (C6).
8. Perform deeper repository/archive/workflow cleanup after active branches stop depending on old coordination files.
9. Freeze a production candidate and run #236 hands-on acceptance.
10. Finish bridge/legacy retirement and stale issue cleanup after replacement paths have production evidence.

## Definition of done

EnthusiaStaff should end in a state where:

- the plugin is documented as the live Enthusia moderation/staff platform;
- owner policy can be changed safely through versioned configuration without routine Java edits;
- messages and GUI presentation are broadly configurable;
- rank capabilities are data-driven while authorization remains fail-safe;
- Policy v2 is the single configurable punishment-policy engine;
- old transitional infrastructure is retired;
- repository navigation is clear and historical material is clearly separated;
- the active GitHub issue set reflects real remaining work;
- a frozen candidate has passed automated and hands-on acceptance.

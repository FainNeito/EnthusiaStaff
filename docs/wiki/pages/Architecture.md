# Architecture

EnthusiaStaff is a distributed moderation platform spanning Minecraft servers/proxy, MariaDB, Discord, and web surfaces. Domain policy and durable state are separated from Paper, Velocity, StaffBot/JDA, Cloudflare/browser UI, provider adapters, and persistence implementation details.

## Quick orientation

- **What is merged?** [[Implementation Status]]
- **Where does a feature live?** [[Developer Code Guide]]
- **How does Discord work?** [[Discord Moderation Platform]]
- **How is StaffBot operated?** [[Staff Bot Runtime and Operations]]
- **How do the site/web APIs fit together?** [[Website and Web API]]
- **What should a reviewer verify?** [[Code Review Guide]]
- **How is a claim proven?** [[Build and Testing]]
- **Paper/Velocity transport:** [[Protocol and Network Traffic]]

## Runtime and component shape

Current merged `main` has three principal Java runtime artifacts:

1. `EnthusiaStaff-Paper-<version>.jar`
2. `EnthusiaStaff-Velocity-<version>.jar`
3. `EnthusiaStaff-StaffBot-<version>.jar`

It also contains web components that are deployed differently:

- `components/enthusia-site/` — public Enthusia site + Cloudflare Pages Functions;
- `moderation-web/` — staging-only Cloudflare Worker/static-assets moderation workspace.

The website components are not Minecraft plugin JARs, and the moderation web workspace is not the StaffBot runtime.

## Gradle modules

| Module | Responsibility |
| --- | --- |
| `common` | shared identifiers, validation, cryptography/security primitives and bounded utilities |
| `domain` | business policy, authorization, application services, state machines and ports |
| `integration-contracts` | stable compile-time contracts for supported Enthusia-owned providers |
| `discord-platform-api` | provider-neutral managed-role contract for Discord platform consumers/providers |
| `persistence` | MariaDB bootstrap, Flyway, JDBC stores, transactions, leases, journals, inboxes/outboxes, recovery |
| `protocol` | authenticated Paper–Velocity transport, replay protection and acknowledgements |
| `paper` | commands, GUIs/listeners, linking/player-state adapters and server-local effects |
| `paper-authority-bridge` | narrow private authority surface used where a non-Paper runtime needs centrally authorized Minecraft-side decisions/effects |
| `velocity` | proxy enforcement, network identity, transport workers, migration and authoritative website API boundary |
| `staff-bot` | standalone Java/JDA Discord runtime, moderation/read UI, Discord effects/reconciliation, private read/launch services and health |
| `integration-tests` | MariaDB/cross-module/concurrency/recovery validation; never deployed |

Root references: [build](https://github.com/wsg138/EnthusiaStaff/blob/main/build.gradle.kts) and [settings](https://github.com/wsg138/EnthusiaStaff/blob/main/settings.gradle.kts).

## Dependency direction

```text
                    Browser / Discord / Minecraft
                         |      |       |
                         v      v       v
                web adapters  StaffBot  Paper/Velocity
                     |           |          |
                     +-----------+----------+
                                 |
                                 v
                     domain policy / ports
                       ^        ^        ^
                       |        |        |
                 persistence  protocol  provider adapters
                                         |
                                         v
                               integration-contracts

Consumer plugins --> discord-platform-api --> StaffBot provider (when wired)
```

The practical rule remains: **domain policy decides; platform adapters translate/apply runtime effects; persistence implements durable ports.**

`integration-contracts` and `discord-platform-api` are compile-time/service boundaries, not second homes for business policy. A Discord command, website route, GUI, Paper listener, or provider adapter should not gain its own copy of punishment ladders, rank hierarchy, target protection, transaction rules, or recovery policy.

## Principal bounded contexts

Important contexts now include:

- Minecraft/Discord identity, account linking and player directory;
- cases, punishments, sanctions, escalation and exact sanction lifecycle;
- reports/evidence and appeals;
- Discord staff moderation/read UX and Discord punishment reconciliation;
- managed Discord role/platform contracts;
- alts and protected network identity;
- inventory, economy, market and reputation moderation;
- staff sessions, staff tools, Cheat Tester, vanish and freeze;
- public website projections and website appeal workflow;
- migration/shadow/cutover;
- audit, configuration and operational health;
- external/provider integrations.

Each context should expose a stable application-service/port boundary rather than allowing unrelated modules to reach directly into another context’s tables or platform implementation.

## Paper ownership

Paper owns server-local Bukkit/Paper state and player/entity mutation:

- staff commands and GUIs;
- player/entity mutations;
- Staff Mode state application/restoration;
- vanish/freeze/player-state enforcement;
- inventory/Ender state and confiscation;
- report/client context capture;
- Minecraft-side Discord linking commands/adapters;
- Paper-side provider adapters.

Important composition paths:

- [EnthusiaStaffPaperPlugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [PaperRuntimeLifecycle](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [PaperRuntimeComponents](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [PaperStorageBindings](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [PaperCommandRegistrar](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java)

Blocking DB/network/provider work must not run on the game/entity thread. Player/entity mutation must return to the supported owning scheduler. Async callbacks that can outlive disconnect/reconnect require session/generation fencing.

Player-originated destructive Paper mutations now also have Staff Mode authority requirements where the owning command/service declares them. That local duty rule does not replace independent Discord/global/website authorization.

## Velocity ownership

Velocity owns proxy/network-facing coordination such as:

- login/server-switch enforcement;
- network player/server presence and protected identity observations;
- persistent Paper–Velocity transport server/workers;
- legacy Discord webhook delivery worker;
- migration/shadow/cutover coordination;
- authoritative website API server/router and public/appeal bridge.

Important paths:

- [EnthusiaStaffVelocityPlugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [VelocityConfiguration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)
- [NetworkOutboxWorker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/NetworkOutboxWorker.java)
- [DiscordOutboxWorker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [WebsiteApiServer](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)
- [WebsiteApiRouter](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)

Velocity event threads must not block on JDBC, HTTP, filesystem or socket I/O. Startup/reload/shutdown changes should be reviewed as lifecycle publication/rollback problems, not only individual methods.

## StaffBot ownership

StaffBot owns the privileged Discord Gateway/JDA lifecycle and Discord-native effects.

It is responsible for:

- Discord application/guild/environment fencing;
- slash/context/component moderation UX;
- linked-staff actor resolution and action-time authority checks;
- Discord-only punishment execution/reconciliation where enabled;
- private moderation-read API used by the staging web workspace;
- signed moderation-workspace launch issuance;
- health/readiness and bounded worker lifecycle.

Key paths:

- [StaffBotApplication](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotApplication.java)
- [StaffBotRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotRuntime.java)
- [JdaDiscordGateway](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordGateway.java)
- [DiscordPunishmentRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentRuntime.java)
- [ModerationReadApiServer](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadApiServer.java)

Discord roles/command visibility never become final moderation authority by themselves. See [[Discord Moderation Platform]].

## Website and browser boundaries

There are three distinct web-facing boundaries:

1. **Velocity website API** — authoritative EnthusiaStaff-side public projections and authenticated appeal/reviewer workflow.
2. **`components/enthusia-site/`** — public site and Cloudflare Pages Functions consuming/mediating approved APIs.
3. **`moderation-web/`** — staging-only Cloudflare moderation workspace, using signed launches and the private StaffBot read API.

The browser/Cloudflare workspace is not trusted as a moderation database client or punishment writer. Public site projections are explicitly allowlisted and must not expose staff-private evidence, raw network identity, credentials or internal recovery state.

Deep dive: [[Website and Web API]].

## MariaDB authority and persistence

MariaDB is the durable authority for moderation/recovery state such as cases, sanctions, identity, reports/evidence, staff sessions, player-state journals, network/Discord delivery, Discord moderation/linking state, migration state, configuration versions, audit, leases and quarantine.

Primary entry points:

- [MariaDb](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDb.java)
- [MariaDbRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/MariaDbRuntime.java)
- [persistence package](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)

Current merged `main` contains migrations through **`V20__discord_account_linking.sql`**. V17 owns website appeal workflow state, V18 Cheat Tester recovery, V19 Discord moderation persistence, and V20 Discord/Minecraft account linking. Applied Flyway history is immutable; new schema work adds a later forward migration.

## Authoritative write pattern

High-risk writes should make these boundaries explicit:

1. normalize/validate input and identity;
2. resolve current actor/target authority;
3. establish idempotency/durable intent where required;
4. acquire required row lock/lease/fence;
5. reread/revalidate current revision and authority;
6. persist recovery/before-state before destructive effects where required;
7. commit authoritative domain state/audit/outbox atomically where the model requires it;
8. apply external/platform side effects idempotently or with ambiguity-aware reconciliation;
9. verify resulting state;
10. record acknowledgement/terminal state or quarantine unresolved ambiguity.

Success should not be reported merely because bytes were sent to Discord, a provider returned 2xx, or a browser request completed.

## Distributed delivery and external effects

Paper–Velocity transport remains at-least-once; correctness comes from authentication/versioning, replay protection, durable inbox/outbox state, idempotent consumers, acknowledgements, bounded retry, reconnect recovery and stale-worker fencing.

Discord native effects are a different external distributed boundary. Some operations cannot be safely blind-retried after an ambiguous remote outcome, so StaffBot verifies/reconciles state before deciding whether to retry or finalize.

Legacy webhook delivery is separate again: it is outbound notification delivery and does not own interactive Discord moderation.

## Safe-failure principles

- A punishment cannot partially apply an intended combined decision without explicit reconciliation state.
- Exact sanction changes cannot mutate unrelated sanctions.
- Stale revisions/confirmations cannot overwrite newer authority/state.
- Inventory/economy/confiscation ambiguity preserves recovery evidence or quarantines the workflow.
- Migration mismatch blocks authority transition.
- Missing optional integrations disable only dependent behavior when safe.
- MariaDB/proxy/provider/authority loss blocks actions whose correctness cannot be established.
- Restart/reconnect must not let stale callbacks/workers mutate new sessions.
- Discord ambiguous effects reconcile rather than blind-retry.
- Account-link codes remain short-lived, one-use and hashed at rest.
- Browser/public APIs never become implicit privileged moderation authority.

## Stable service boundaries

Important internal/public contracts include `StaffVisibilityService`, `PunishmentQueryService`, `SanctionQueryService`, `StaffSessionService`, `StaffModeQueryService`, `InventoryLockService`, `AltRelationshipService`, `PlayerDirectoryService`, provider contracts under `integration-contracts`, and managed-role contracts under `discord-platform-api`.

Other plugins should depend on supported contracts/services rather than mutable EnthusiaStaff internals or raw tables.

## How to continue

- Discord product behavior? [[Discord Moderation Platform]]
- StaffBot operations? [[Staff Bot Runtime and Operations]]
- Website/API/browser architecture? [[Website and Web API]]
- Exact class/store/test trace? [[Developer Code Guide]]
- Reviewing a change? [[Code Review Guide]]
- Debugging a failure? [[Recovery and Troubleshooting]]
- Validating a claim? [[Build and Testing]]
- Feature family? [[Core Platform and Infrastructure]], [[Moderation, Punishments, and Reports]], [[Staff Tools, Investigations, and Player-State Safety]], or [[Integrations, Migration, and Release Readiness]].
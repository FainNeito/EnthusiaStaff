# Player investigation tools

These additions are proposed source features, not production activation. Flags are staff observations and never apply punishments or restrictions. LiteBans cutover remains separate.

## Patrol and activity

Random staff teleport prefers players that the requesting staff member has not recently visited. Only successful teleports count. Previously visited players remain available when fresh candidates become ineligible or the eligible pool is exhausted. History is bounded to 20 successful targets by default, configurable through `staff-tools.random-teleport.recent-targets` (0–1000), and cleared on disconnect.

`skip-idle: true` optionally excludes players whose last locally observed movement/action is older than `idle-seconds` (30–3600, default 300). This heuristic does not consume an external AFK provider or infer cheating. Unknown activity remains eligible. Existing staff-mode, vanish, freeze, exemption, world, player-state and actor-authority checks still apply; the chosen target is revalidated on its entity scheduler.

`/inspect <player>` displays latest left/right click, crouch, successful block-place and block-break observations with `enthusiastaff.inspect.activity`. Records are local to this backend/session, bounded to 10,000 players, and removed on quit and shutdown. Movement supports idle filtering but is not listed as a cheating signal. Clicks represent observed input, including Bukkit's pre-cancelled air interactions; cancelled block mutations are not recorded.

## Durable flags and notes

All new permissions default to false and require explicit grants plus an explicit staff rank. Flag edits require ACTIVE mode.

| Permission | Access |
| --- | --- |
| `enthusiastaff.investigation.view` | List active flags and see them in the inspector |
| `enthusiastaff.investigation.edit` | Create/resolve flags |
| `enthusiastaff.investigation.notes.view` | Read recent notes in the inspector |
| `enthusiastaff.investigation.join-alerts` | Receive join summaries, with separate content permissions |
| `enthusiastaff.inspect.activity` | Read local activity observations |

Commands work with directory names or UUIDs, including existing Java/Bedrock identities:

```text
/staffflags list <player>
/staffflags add <player> <category> <hours|permanent> <case-id|none> <reason>
/staffflags resolve <flag-uuid> <reason>
```

Categories are configured under `investigation.flags.categories`; defaults are `watch`, `suspected-cheating`, `behavior`, and `follow-up`. Reasons are required and limited to 500 printable characters. Finite expiry is 1–8760 hours. Optional case IDs must resolve to the target player. Creation/resolution retain actor, reason and timestamps in a separate immutable audit table within the same database transaction. Expired/resolved flags are excluded from active views; resolution is idempotent and does not erase history. Queries return at most 20 active flags and 10 recent notes.

Enable join alerts through `investigation.join-alerts.enabled`. Alerts contain bounded counts of active flags and notes from the last 30 days, plus an inspect shortcut; they do not broadcast reasons or note text. Delivery checks current permissions, explicit staff rank, target visibility and the joining session token. Reconnect alerts are limited to one per viewer/target per five minutes and one per viewer every two seconds. If workers/storage are unavailable, joining proceeds without the best-effort alert.

## Slots, reload and release

`staff-tools.slots` is a unique permutation of slots 0–8 for the nine existing tool IDs. Unknown IDs, duplicate/out-of-range/non-integer slots fail configuration validation. Item dispatch still checks the live owner/session, material, rank, permissions and configured slot. Existing `/stafftools` shortcuts retain normal service authorization; configuration cannot introduce console command chains.

All these settings are restart-only. A reload changing patrol, slots, cooldowns, flag categories or join alerts rejects the entire candidate and reports the restart requirement.

The proposed schema is `V24__player_investigation_flags.sql`. At the starting main head V21 exists; other open PRs contain competing V22 migrations and V23. Resolve migration sequencing before merging/releasing this schema. Do not alter deployed migrations, enable out-of-order migration as a workaround, or deploy this unmerged branch.

Local unit tests and JAR creation do not establish MariaDB upgrade, staging, cross-backend, or player/client acceptance. The MariaDB integration suite covers V21 upgrade, restart persistence, expiry, audit rollback and idempotent resolution when a container runtime is available.

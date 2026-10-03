# Polar Anticheat → EnthusiaStaff Punishment Bridge

## Overview

Polar's `ban_commands` and `soft_ban_commands` now route through EnthusiaStaff's central
punishment system via the `/staffapi` console command. Every Polar punishment gets:
- Database logging (cases, sanctions, audit trail)
- Discord webhook to `#in-game-punishments` with rich details
- Automatic escalation via the `cheating.polar.template` reason ladder
- Commit effects (kick/ban enforcement)

## The Command

```
/staffapi punish <player> <ban|kick|mute|warn> [reason...] [--checks=<detail>]
```

| Argument | Description |
|----------|-------------|
| `<player>` | Player name (resolved via PlayerDirectory) |
| `<ban\|kick\|mute\|warn>` | Punishment type |
| `[reason...]` | Free-text reason (default: "Cheating") |
| `[--checks=<detail>]` | Optional anticheat check details (e.g. "KillAura vl=45") |

**Actor:** All punishments are issued as `Polar Anticheat` (StaffRank.SYSTEM).

**Permission:** `enthusiastaff.api.punish` (console bypasses permission checks).

## Punishment Mapping

| Type | Behavior |
|------|----------|
| `ban` | Uses `cheating.polar.template` reason ladder: 30d → 30d → 60d → 90d NETWORK_BAN (escalates automatically) |
| `kick` | Instant KICK sanction |
| `mute` | 1-day MUTE sanction |
| `warn` | Instant WARNING sanction |

## Polar Configuration

In `plugins/Polar/polar.yml`:

```yaml
ban_commands:
  - "staffapi punish {player} ban Cheating --checks={check}"
soft_ban_commands:
  - "staffapi punish {player} kick Cheating --checks={check}"
```

### Confirmed Placeholders

| Placeholder | Description | Status |
|-------------|-------------|--------|
| `{player}` | Player name | ✅ Confirmed (used in current config) |
| `{check}` | Anticheat check name | ⚠️ Verify in Polar docs |

> **Note:** `{check}` was not confirmed from Polar's documentation (not publicly available).
> Check your Polar version's docs or test with a harmless command first. If `{check}`
> is not supported, omit the `--checks` flag — the punishment will still work.

## Examples

```
# Polar is certain — escalating ban
staffapi punish Notch ban Cheating --checks=KillAura

# Polar suspects — kick with details
staffapi punish Notch kick Suspicious movement --checks=Fly vl=12

# Simple (no check details)
staffapi punish Notch ban Cheating
```

## Discord Output

Each punishment posts to `#in-game-punishments` with:
- Player name and UUID
- Punishment type and duration
- Actor: "Polar Anticheat"
- Reason and anticheat check details
- Case ID for staff reference

## Idempotency

Each command generates a unique idempotency key (`staffapi:<uuid>:<type>:<timestamp>`).
If Polar retries a command, it will create a new case (not deduplicated). This is intentional —
each Polar trigger represents a separate detection event.

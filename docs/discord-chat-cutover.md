# Discord chat cutover and rollback

Tracking: issue `#268` under DiscordSRV retirement umbrella `#264`.

This document governs the chat portion of DiscordSRV retirement. It does not authorize removal of
DiscordSRV's other responsibilities.

## Authority modes

Paper and StaffBot intentionally require separate explicit configuration.

### Paper

`discord-chat-bridge.mode` accepts:

- `DISABLED` — provider-neutral Discord chat transport is not installed.
- `SHADOW` — replacement transport is installed but RoseChat's legacy DiscordSRV chat path stays live.
- `AUTHORITATIVE` — replacement transport is installed and RoseChat's legacy Discord chat path is
  suppressed only after replacement readiness succeeds.

For backward compatibility, if `mode` is omitted,
`discord-chat-bridge.shadow-enabled=true` still selects `SHADOW`.

Paper `AUTHORITATIVE` also requires:

```yaml
discord-chat-bridge:
  mode: AUTHORITATIVE
  shadow-enabled: false
  authoritative-cutover-ack: true
```

Do not leave the legacy `shadow-enabled` flag true with an explicit non-SHADOW mode.

### StaffBot

Preferred environment variable:

```text
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=DISABLED|SHADOW|AUTHORITATIVE
```

The old `ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ENABLED=true` remains a staging compatibility alias for
`SHADOW` only when `MODE` is omitted.

Production requires:

```text
ENTHUSIA_STAFF_BOT_ENVIRONMENT=production
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=AUTHORITATIVE
ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_CUTOVER_ACK=I_ACKNOWLEDGE_DISCORDSRV_CHAT_CUTOVER
```

AUTHORITATIVE also requires at least one explicit symmetric
`ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_INGRESS_ROUTES` entry. This prevents legacy Discord inbound from
being suppressed when no replacement Discord -> Minecraft route exists.

Production never accepts the legacy boolean by itself and never accepts `SHADOW`.

## What AUTHORITATIVE changes

The replacement transport must already have:

- RoseChat plain outbound bridge;
- RoseChat styled outbound bridge;
- Discord -> Minecraft inbound bridge;
- authenticated Paper -> Velocity channel connection.

When both InteractiveChat and `InteractiveChatDiscordSrvAddon` are currently enabled, the
temporary rich-render compatibility provider must also be registered successfully.

Only after those readiness checks pass does EnthusiaStaff call RoseChat's
`suppressLegacyDiscordChat()` registration.

The registration affects only RoseChat's legacy Discord chat path. It does not disable:

- RoseChat public chat;
- Staff moderation/preflight;
- Minecraft -> Minecraft/Bungee routing;
- provider-neutral outbound publication;
- provider-neutral inbound Discord chat;
- DiscordSRV itself or its unrelated features.

The suppression registration is released **before** replacement bridge teardown or Paper channel
unbind. Releasing it makes RoseChat's legacy Discord chat path eligible again.

## Staging acceptance matrix

Do not enter production `AUTHORITATIVE` until all applicable rows have been observed on a
controlled server with the actual dependency set.

| Case | Expected Minecraft -> Discord | Expected Discord -> Minecraft |
| --- | --- | --- |
| Normal public chat | one clean StaffBot message, correct RoseChat sender/rank/text | one RoseChat message, no echo back to Discord |
| Legacy `&` / section colors | no raw formatting codes leak | readable text reaches RoseChat |
| Hex/RGB chat | readable Discord fallback; exact Adventure render retained internally | readable text reaches RoseChat |
| Bold/italic/underline/etc. | clean supported Discord Markdown where representable | no malformed formatting injection |
| InteractiveChat held item | styled text plus bounded PNG artifact | n/a |
| InteractiveChat inventory | styled text plus bounded PNG artifact | n/a |
| InteractiveChat Ender chest | styled text plus bounded PNG artifact | n/a |
| Escaped InteractiveChat placeholder | no artifact for escaped placeholder | n/a |
| Oversize image/artifact | text-only fallback, no duplicate Discord message | n/a |
| Missing attachment permission | text-only fallback | n/a |
| Linked Minecraft account | optional cached linked display label; no raw Discord ID | n/a |
| Unlinked Minecraft account | normal Minecraft-only sender prefix | n/a |
| Discord user/role/channel mention text | mentions disabled on send | readable normalized text, no delegated DiscordSRV parsing |
| Bot/webhook Discord message | n/a | ignored |
| Private/non-public RoseChat channel | no public Discord export | only explicitly configured public route accepted |
| StaffBot queue saturation | Minecraft chat still succeeds; bridge may drop | Discord message may drop; no durable backlog |
| Velocity/Paper disconnect | legacy send restored on Paper channel unbind in AUTHORITATIVE mode | no stale replay |
| RoseChat reload/disable | legacy suppression released | bridge revalidated after return |
| InteractiveChat/addon disable | legacy suppression released when temporary compatibility renderer was part of readiness | n/a |
| Duplicate transport frame | no duplicate final chat send | no duplicate Minecraft delivery |

## Shadow validation sequence

1. Keep Paper in `SHADOW`.
2. Keep StaffBot in staging `SHADOW`.
3. Route only the pinned staging test channel.
4. Leave DiscordSRV fully live.
5. Compare the replacement output against Minecraft/RoseChat semantics, not DiscordSRV's formatting
   quirks.
6. Exercise every applicable row in the acceptance matrix.
7. Verify bounded queue/drop behavior and no durable chat replay.
8. Verify server/channel route maps are exact and symmetric where inbound is enabled.
9. Verify the StaffBot application has Message Content intent only when inbound routes are configured.
10. Verify no public message can create Discord mentions.

## Production cutover sequence

Production cutover is a deliberate configuration operation, not a merge side effect.

1. Record the current known-good DiscordSRV/RoseChat configuration for rollback.
2. Configure the production StaffBot TLS/HMAC channel peer and explicit route maps.
3. Verify every production Discord channel ID belongs to the pinned Enthusia guild and StaffBot has
   `VIEW_CHANNEL` + `MESSAGE_SEND`.
4. Enable Message Content intent if production inbound routes are configured.
5. Start StaffBot with `MODE=AUTHORITATIVE` and the exact cutover acknowledgement.
6. Configure Paper with `mode: AUTHORITATIVE` and `authoritative-cutover-ack: true`.
7. Restart/apply the restart-only chat configuration using the normal deployment process.
8. Confirm the RoseChat authority issue is clear and the authenticated Paper channel is connected.
9. Send one controlled Minecraft message and confirm exactly one Discord message appears.
10. Send one controlled Discord message and confirm exactly one Minecraft/RoseChat message appears.
11. Test at least one InteractiveChat artifact if InteractiveChat is installed.
12. Observe normal chat long enough to detect duplicate/loop/routing behavior before proceeding with
    any DiscordSRV jar removal.

Do **not** remove DiscordSRV merely because chat cutover succeeds. Complete the separate account-link,
managed-role, console, and any other DiscordSRV responsibility migrations under #264 first.

## Immediate rollback

Rollback should favor restoring chat availability over preserving the new transport.

1. Set Paper chat mode to `SHADOW` or `DISABLED` and restart/apply through the normal deployment
   path. Closing/unbinding EnthusiaStaff releases the RoseChat legacy suppression registration.
2. Confirm RoseChat's legacy Discord chat path is active again.
3. Set StaffBot chat mode to `DISABLED` after legacy delivery is confirmed.
4. Keep the exact replacement route/config values available for diagnosis; do not delete logs or
   change multiple unrelated Discord systems simultaneously.
5. Verify one Minecraft -> Discord and one Discord -> Minecraft legacy message before declaring
   rollback complete.

If the Paper process/channel fails unexpectedly, the suppression registration is process-local and
does not survive RoseChat/EnthusiaStaff shutdown. RoseChat therefore returns to its normal legacy
eligibility when the plugin lifecycle restarts without a successful authoritative acquisition.

## Current external validation limit

Hosted CI and canonical Pi/Sentinel startup tests do not currently include trusted artifacts for
InteractiveChat, `InteractiveChatDiscordSrvAddon`, or DiscordSRV. They can validate the
EnthusiaStaff build/start/restart contract, but they cannot prove visual parity of the real
InteractiveChat renderer.

Real rich-render acceptance therefore requires either:

- reviewed trusted dependency onboarding for the exact upstream dependency closure; or
- a controlled SMP-like staging environment with the real plugins installed.

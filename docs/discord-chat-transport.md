# Discord chat transport foundation

Tracking: `#268` under DiscordSRV retirement umbrella `#264`.

## Confirmed topology

The persistent channel now carries two distinct authenticated peer classes:

- required Paper backend peers such as `SMP` and `HUB`;
- the reserved auxiliary peer `STAFFBOT`.

`STAFFBOT` is authenticated on the same TLS 1.3 + HMAC transport, but it is **not** a durable
moderation backend and is never included in `NetworkOutboxWorker` delivery quorum.

The staged outbound path is:

```text
RoseChat
  -> EnthusiaStaff Paper
  -> existing authenticated persistent channel
  -> Velocity ephemeral chat relay
  -> authenticated STAFFBOT peer
  -> bounded StaffBot chat queue
  -> StaffBot JDA
```

The reverse Discord -> Minecraft path remains separate. It must route explicitly to a target
backend and enter the canonical RoseChat pipeline without bypassing moderation or creating an echo
loop.

## Outbound wire contract

`ChatBridgeOutboundMessage` is the provider-neutral transport contract.

It contains only:

- stable RoseChat event ID;
- stable Minecraft-side external message ID;
- canonical mirror ID;
- creation and hard-expiry timestamps;
- original Paper backend ID;
- logical RoseChat channel ID;
- Minecraft sender UUID;
- bounded presentation name;
- bounded canonical plain text.

It deliberately contains no JDA, DiscordSRV, Discord role, Discord user, database record, or
moderation-internal type.

`ChatBridgeMessages.OUTBOUND` is `CHAT_BRIDGE_OUTBOUND_V1`.

The JSON payload is limited to 16 KiB. Canonical text is limited to 2,000 characters. The
message lifetime cannot exceed 60 seconds. Routing/presentation identifiers reject control
characters. Unknown JSON properties are rejected so a newer sender cannot silently widen what an
older relay accepts.

## Delivery semantics

Chat remains best-effort and ephemeral:

- no MariaDB outbox;
- no replay after expiry;
- no hours-old delivery after reconnect;
- an ACK confirms only that the next authenticated hop accepted the frame, not that Discord
  displayed it;
- transport failure never blocks Minecraft chat;
- Paper, Velocity, and StaffBot queues are bounded;
- queue saturation, missing routes, disconnects, expiry, or JDA failures drop chat instead of
  creating a retry backlog.

The existing persistent-channel security properties remain in force: TLS 1.3,
HMAC-authenticated envelopes, nonce/timestamp replay protection, frame bounds, explicit peer IDs,
and per-message acknowledgement.

## Paper -> Velocity SHADOW checkpoint

Merged PR #370 established the first runtime hop:

- EnthusiaStaff Paper mirrors only RoseChat's public outbound bridge API in
  `integration-contracts`; those types remain compile-time-only and are not shaded into the Paper
  JAR.
- Paper installs the RoseChat outbound provider only when
  `discord-chat-bridge.shadow-enabled: true`. The flag defaults **false**.
- The provider binds to the already-authenticated `PersistentChannelClient` when that channel is
  live and unbinds during channel shutdown/reconnect.
- The RoseChat caller never performs a socket write. Paper uses a bounded single-thread in-memory
  relay queue and drops chat when disconnected, expired, or saturated.
- Paper sends `CHAT_BRIDGE_OUTBOUND_V1` with the RoseChat event ID as the authenticated envelope
  message ID.
- Velocity intercepts this message type **before** the durable network inbox. Chat is never recorded
  in `NetworkOutboxStore`.
- Velocity requires the payload `sourceServerId` to equal the authenticated Paper envelope
  `serverId`, and the payload event ID to equal the envelope message ID.
- Velocity uses bounded in-memory queue/dedupe state. Already-admitted duplicates are ACKed without
  duplicate delivery.

Enabling SHADOW does not disable RoseChat's existing DiscordSRV send. DiscordSRV remains the live
Minecraft <-> Discord chat transport.

## Velocity -> StaffBot staging checkpoint

PR #381 adds the next bounded leg while remaining default-off and staging-only:

- Velocity reserves peer ID `STAFFBOT`. Configure its HMAC key only by adding
  `channel.backend.STAFFBOT.secret-environment=...` to Velocity's private runtime config.
- The peer-policy layer removes `STAFFBOT` from the required Paper backend set before
  `NetworkOutboxWorker` is constructed.
- Authenticated `STAFFBOT` application frames are rejected before chat, transfer, staff-mode,
  verification/report, or durable-inbox handlers. Authentication never implies Paper-backend
  application authority.
- When the peer key is configured, Velocity installs one chat sink that sends only
  `CHAT_BRIDGE_OUTBOUND_V1` to `STAFFBOT` and waits for the normal short ACK on the dedicated
  Velocity chat worker.
- Missing/disconnected/rejected StaffBot delivery returns false to the ephemeral relay. There is no
  durable retry path.
- StaffBot connects outbound with `PersistentChannelClient`; transport reconnect is independent
  from core bot readiness.
- StaffBot accepts chat only while a validated Discord identity is current. Disconnect pauses
  admission and clears queued/dedupe state; a revalidated session resumes it.
- StaffBot routes only an explicit `sourceServer/logicalChannel -> Discord channel ID` allowlist.
  During this checkpoint every route must target the fixed staging test channel.
- StaffBot uses a dedicated bounded single-thread chat queue and bounded event-ID dedupe.
- JDA egress validates that the target channel belongs to the pinned Enthusia guild and that the bot
  has `VIEW_CHANNEL` + `MESSAGE_SEND`.
- Final Discord content is bounded to 2,000 characters after the `[server] sender: ` prefix.
- Allowed mentions are set to an empty list for every chat send.

StaffBot chat configuration is disabled unless
`ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ENABLED=true`, and enabling it is rejected outside the staging
StaffBot environment.

## Still out of scope

This checkpoint does not:

- carry InteractiveChat rich-render artifacts;
- support Discord -> Minecraft ingress;
- authorize production Discord routing;
- change or disable DiscordSRV;
- authorize DiscordSRV cutover/removal.

Those remain separate reviewable checkpoints.

# Discord chat transport foundation

Tracking: `#268` under DiscordSRV retirement umbrella `#264`.

## Confirmed topology

The current persistent channel is an authenticated **Paper backend <-> Velocity** transport.
StaffBot does not currently instantiate `PersistentChannelClient`, `PersistentChannelServer`, or
`ChannelMessageHandler`.

Therefore the chat migration must not pretend there is already a direct Paper <-> StaffBot socket.

The intended staged path is:

```text
RoseChat
  -> EnthusiaStaff Paper
  -> existing authenticated persistent channel
  -> Velocity relay
  -> bounded authenticated StaffBot relay leg
  -> StaffBot JDA
```

The reverse Discord -> Minecraft path will be designed separately after outbound delivery is
reviewed. It must route explicitly to a target backend and enter the canonical RoseChat pipeline
without bypassing moderation or creating an echo loop.

## Outbound wire checkpoint

`ChatBridgeOutboundMessage` is the first transport-level contract.

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
- transport failure must never block Minecraft chat;
- future relay queues must be bounded and drop expired work under pressure.

The existing `PersistentChannelClient` / `PersistentChannelServer` security properties remain in
force for the Paper <-> Velocity hop: TLS 1.3, HMAC-authenticated envelopes, nonce/timestamp replay
protection, frame bounds, explicit backend IDs, and per-message acknowledgement.

## Paper -> Velocity SHADOW checkpoint

This branch now implements the first runtime hop in addition to the wire contract:

- EnthusiaStaff Paper mirrors only RoseChat's public outbound bridge API in `integration-contracts`; those types remain compile-time-only and are not shaded into the Paper JAR.
- Paper installs the RoseChat outbound provider only when `discord-chat-bridge.shadow-enabled: true`. The flag defaults **false**.
- The provider binds to the already-authenticated `PersistentChannelClient` when that channel is live and unbinds during channel shutdown/reconnect.
- The RoseChat caller never performs a socket write. Paper uses a bounded single-thread in-memory relay queue and drops chat when disconnected, expired, or saturated.
- Paper sends `CHAT_BRIDGE_OUTBOUND_V1` with the RoseChat event ID as the authenticated envelope message ID.
- Velocity intercepts this message type **before** the durable network inbox. Chat is never recorded in `NetworkOutboxStore`.
- Velocity requires the payload `sourceServerId` to equal the authenticated envelope `serverId`, and the payload event ID to equal the envelope message ID.
- Velocity uses a bounded in-memory queue and bounded short-lived dedupe state. Already-admitted duplicates are ACKed without duplicate delivery.
- The Velocity relay has a single-owner sink seam for the next StaffBot transport checkpoint; with no sink installed it does not claim delivery.

Enabling SHADOW does not disable RoseChat's existing DiscordSRV send. DiscordSRV remains the live Minecraft <-> Discord chat transport.

## Not implemented by this checkpoint

This checkpoint still does not:

- connect StaffBot to the Velocity relay;
- call JDA through the new path;
- carry InteractiveChat render artifacts;
- support Discord -> Minecraft ingress;
- change or disable DiscordSRV;
- authorize production cutover.

Those remain separate reviewable checkpoints.

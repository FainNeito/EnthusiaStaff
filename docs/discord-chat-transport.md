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

## Not implemented by this checkpoint

This contract does not yet:

- install the RoseChat bridge provider from EnthusiaStaff Paper;
- relay chat through Velocity;
- connect StaffBot to the relay;
- call JDA;
- carry InteractiveChat render artifacts;
- support Discord -> Minecraft ingress;
- change or disable DiscordSRV;
- authorize production cutover.

Those remain separate reviewable checkpoints.

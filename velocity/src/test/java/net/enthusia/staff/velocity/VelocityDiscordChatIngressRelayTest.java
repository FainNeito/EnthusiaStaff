package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeInboundMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class VelocityDiscordChatIngressRelayTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void admitsOneAuthenticatedIngressFrameToExactPaperBackend() throws Exception {
        VelocityDiscordChatIngressRelay relay = new VelocityDiscordChatIngressRelay(
                Set.of("SMP"), CLOCK, 8, 32);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> peer = new AtomicReference<>();
        AtomicReference<String> type = new AtomicReference<>();
        AtomicReference<ChatBridgeInboundMessage> forwarded = new AtomicReference<>();
        Object binding = new Object();
        relay.bindForTest(binding, (peerId, messageId, messageType, payloadJson, timeout) -> {
            peer.set(peerId);
            type.set(messageType);
            forwarded.set(ChatBridgeMessages.decodeInbound(payloadJson));
            delivered.countDown();
            return PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
        });
        ChatBridgeInboundMessage message = message("SMP");

        assertTrue(relay.accept(envelope(message)));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals("SMP", peer.get());
        assertEquals(ChatBridgeMessages.INBOUND, type.get());
        assertEquals(message, forwarded.get());

        relay.close();
    }

    @Test
    void rejectsUnknownTargetExpiredPayloadAndEnvelopeIdMismatch() {
        VelocityDiscordChatIngressRelay relay = new VelocityDiscordChatIngressRelay(
                Set.of("SMP"), CLOCK, 8, 32);
        relay.bindForTest(new Object(), (peerId, messageId, messageType, payloadJson, timeout) ->
                PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED);

        ChatBridgeInboundMessage unknown = message("HUB");
        assertFalse(relay.accept(envelope(unknown)));

        ChatBridgeInboundMessage expired = new ChatBridgeInboundMessage(
                UUID.randomUUID(),
                "discord-1",
                "discord-canonical-1",
                NOW - 30_000,
                NOW - 1,
                1541286004298752091L,
                "123",
                "DiscordUser",
                "SMP",
                "global",
                "old"
        );
        assertFalse(relay.accept(envelope(expired)));

        ChatBridgeInboundMessage valid = message("SMP");
        assertFalse(relay.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                VelocityStaffBotChatSink.PEER_ID,
                ChatBridgeMessages.INBOUND,
                NOW,
                "nonce",
                ChatBridgeMessages.encodeInbound(valid),
                "mac"
        )));

        relay.close();
    }

    @Test
    void suppressesDuplicateEventsAndDoesNotDelegateTwice() throws Exception {
        VelocityDiscordChatIngressRelay relay = new VelocityDiscordChatIngressRelay(
                Set.of("SMP"), CLOCK, 8, 32);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        relay.bindForTest(new Object(), (peerId, messageId, messageType, payloadJson, timeout) -> {
            count.incrementAndGet();
            delivered.countDown();
            return PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
        });
        ChatBridgeInboundMessage message = message("SMP");
        ProtocolEnvelope envelope = envelope(message);

        assertTrue(relay.accept(envelope));
        assertTrue(relay.accept(envelope));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        Thread.sleep(50L);
        assertEquals(1, count.get());

        relay.close();
    }

    private static ChatBridgeInboundMessage message(String targetServer) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeInboundMessage(
                eventId,
                "discord-123456",
                "discord-canonical-123456",
                NOW,
                NOW + 30_000,
                1541286004298752091L,
                "123456789",
                "DiscordUser",
                targetServer,
                "global",
                "hello"
        );
    }

    private static ProtocolEnvelope envelope(ChatBridgeInboundMessage message) {
        return new ProtocolEnvelope(
                1,
                message.eventId(),
                VelocityStaffBotChatSink.PEER_ID,
                ChatBridgeMessages.INBOUND,
                NOW,
                "nonce",
                ChatBridgeMessages.encodeInbound(message),
                "mac"
        );
    }
}

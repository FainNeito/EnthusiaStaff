package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class VelocityChatBridgeRelayTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String SERVER_ID = "SMP";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final Clock EXPIRED_CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW + 30_001L), ZoneOffset.UTC);
    private static final int FIRST_DELIVERY = 1;

    @Test
    void acceptsAuthenticatedIdentityBoundChatWithoutDurableState() throws Exception {
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        AtomicReference<ChatBridgeOutboundMessage> delivered = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        relay.installSink(message -> {
            delivered.set(message);
            latch.countDown();
            return true;
        });
        ChatBridgeOutboundMessage message = message(SERVER_ID, NOW + 30_000L);
        ProtocolEnvelope envelope = envelope(SERVER_ID, message.eventId(), message);

        assertTrue(relay.handles(envelope));
        assertTrue(relay.accept(envelope));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(message, delivered.get());

        relay.close();
    }

    @Test
    void rejectsPayloadServerSpoofAndEventIdentityMismatch() {
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        AtomicInteger deliveries = new AtomicInteger();
        relay.installSink(message -> {
            deliveries.incrementAndGet();
            return true;
        });

        ChatBridgeOutboundMessage spoofedServer = message("HUB", NOW + 30_000L);
        assertFalse(relay.accept(envelope(SERVER_ID, spoofedServer.eventId(), spoofedServer)));

        ChatBridgeOutboundMessage mismatchedEvent = message(SERVER_ID, NOW + 30_000L);
        assertFalse(relay.accept(envelope(SERVER_ID, UUID.randomUUID(), mismatchedEvent)));
        assertEquals(0, deliveries.get());

        relay.close();
    }

    @Test
    void rejectsExpiredMalformedAndUnavailableSink() {
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(EXPIRED_CLOCK, 8, 32);
        ChatBridgeOutboundMessage expired = message(SERVER_ID, NOW + 30_000L);

        assertFalse(relay.accept(envelope(SERVER_ID, expired.eventId(), expired)));
        assertFalse(relay.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                SERVER_ID,
                ChatBridgeMessages.OUTBOUND,
                NOW,
                "nonce",
                "{not-json}",
                "mac"
        )));

        ChatBridgeOutboundMessage valid = message(SERVER_ID, NOW + 60_000L);
        assertFalse(relay.accept(envelope(SERVER_ID, valid.eventId(), valid)));

        relay.close();
    }

    @Test
    void duplicateAcceptedFrameIsAckedWithoutSecondDelivery() throws Exception {
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        relay.installSink(message -> {
            deliveries.incrementAndGet();
            first.countDown();
            return true;
        });
        ChatBridgeOutboundMessage message = message(SERVER_ID, NOW + 30_000L);
        ProtocolEnvelope envelope = envelope(SERVER_ID, message.eventId(), message);

        assertTrue(relay.accept(envelope));
        assertTrue(relay.accept(envelope));
        assertTrue(first.await(2, TimeUnit.SECONDS));
        Thread.sleep(50L);
        assertEquals(1, deliveries.get());

        relay.close();
    }

    @Test
    @Timeout(5)
    void saturatedQueueRejectsNewestFrameWithoutBlocking() throws Exception {
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 1, 32);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDelivered = new CountDownLatch(1);
        AtomicInteger deliveries = new AtomicInteger();
        relay.installSink(message -> {
            int count = deliveries.incrementAndGet();
            if (count == FIRST_DELIVERY) {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            } else {
                secondDelivered.countDown();
            }
            return true;
        });

        ChatBridgeOutboundMessage first = message(SERVER_ID, NOW + 30_000L);
        ChatBridgeOutboundMessage second = message(SERVER_ID, NOW + 30_000L);
        ChatBridgeOutboundMessage third = message(SERVER_ID, NOW + 30_000L);

        assertTrue(relay.accept(envelope(SERVER_ID, first.eventId(), first)));
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        assertTrue(relay.accept(envelope(SERVER_ID, second.eventId(), second)));
        assertFalse(relay.accept(envelope(SERVER_ID, third.eventId(), third)));

        releaseFirst.countDown();
        assertTrue(secondDelivered.await(2, TimeUnit.SECONDS));
        assertEquals(2, deliveries.get());

        relay.close();
    }

    @Test
    void nonChatFramesAreNotClaimed() {
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        ProtocolEnvelope envelope = new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                SERVER_ID,
                "PUNISHMENT_CREATED",
                NOW,
                "nonce",
                "{}",
                "mac"
        );

        assertFalse(relay.handles(envelope));
        relay.close();
    }

    private static ChatBridgeOutboundMessage message(String sourceServerId, long expiresAt) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                expiresAt,
                sourceServerId,
                "global",
                UUID.randomUUID(),
                "Player",
                "hello"
        );
    }

    private static ProtocolEnvelope envelope(
            String authenticatedServerId,
            UUID messageId,
            ChatBridgeOutboundMessage message
    ) {
        return new ProtocolEnvelope(
                1,
                messageId,
                authenticatedServerId,
                ChatBridgeMessages.OUTBOUND,
                NOW,
                "nonce",
                ChatBridgeMessages.encodeOutbound(message),
                "mac"
        );
    }
}

package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.chatbridge.OutboundChatBridge;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatBridgeCoordinator;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatMessage;
import dev.rosewood.rosechat.api.staff.ChannelClassification;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RoseChatOutboundBridgeIntegrationTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void preservesWireIdentityAndSourceServer() throws Exception {
        AtomicReference<OutboundChatBridge> installed = new AtomicReference<>();
        AtomicBoolean registrationClosed = new AtomicBoolean();
        RoseChatOutboundBridgeIntegration integration = integration(
                installed,
                registrationClosed,
                RoseChatOutboundBridgeIntegration.MAXIMUM_QUEUED_MESSAGES
        );
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<UUID> messageId = new AtomicReference<>();
        AtomicReference<String> messageType = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        Object channelIdentity = new Object();
        integration.bindChannelForTest(
                channelIdentity,
                () -> true,
                (id, type, json, timeout) -> {
                    messageId.set(id);
                    messageType.set(type);
                    payload.set(json);
                    delivered.countDown();
                }
        );

        UUID eventId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        OutboundChatMessage outbound = message(eventId, playerId, NOW, NOW + 30_000L, "hello");
        installed.get().publish(outbound);

        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertEquals(eventId, messageId.get());
        assertEquals(ChatBridgeMessages.OUTBOUND, messageType.get());

        ChatBridgeOutboundMessage decoded = ChatBridgeMessages.decodeOutbound(payload.get());
        assertEquals(eventId, decoded.eventId());
        assertEquals(outbound.externalMessageId(), decoded.externalMessageId());
        assertEquals(outbound.canonicalMessageId(), decoded.canonicalMessageId());
        assertEquals("SMP", decoded.sourceServerId());
        assertEquals("global", decoded.logicalChannelId());
        assertEquals(playerId, decoded.minecraftPlayerId());
        assertEquals("Player", decoded.displayName());
        assertEquals("hello", decoded.plainText());

        integration.close();
        assertTrue(registrationClosed.get());
    }

    @Test
    void disconnectedAndExpiredMessagesAreDroppedWithoutTransportWork() throws Exception {
        AtomicReference<OutboundChatBridge> installed = new AtomicReference<>();
        RoseChatOutboundBridgeIntegration integration = integration(
                installed,
                new AtomicBoolean(),
                RoseChatOutboundBridgeIntegration.MAXIMUM_QUEUED_MESSAGES
        );
        AtomicInteger sends = new AtomicInteger();
        integration.bindChannelForTest(
                new Object(),
                () -> false,
                (id, type, json, timeout) -> sends.incrementAndGet()
        );

        installed.get().publish(message(
                UUID.randomUUID(), UUID.randomUUID(), NOW, NOW + 30_000L, "disconnected"));
        installed.get().publish(message(
                UUID.randomUUID(), UUID.randomUUID(), NOW - 10_000L, NOW - 1L, "expired"));

        Thread.sleep(100L);
        assertEquals(0, sends.get());
        integration.close();
    }

    @Test
    @Timeout(5)
    void saturationDropsNewestWorkInsteadOfBlockingChatCaller() throws Exception {
        AtomicReference<OutboundChatBridge> installed = new AtomicReference<>();
        RoseChatOutboundBridgeIntegration integration = integration(
                installed,
                new AtomicBoolean(),
                1
        );
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDelivered = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        integration.bindChannelForTest(
                new Object(),
                () -> true,
                (id, type, json, timeout) -> {
                    int sequence = sends.incrementAndGet();
                    if (sequence == 1) {
                        firstStarted.countDown();
                        try {
                            releaseFirst.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        secondDelivered.countDown();
                    }
                }
        );

        installed.get().publish(message(
                UUID.randomUUID(), UUID.randomUUID(), NOW, NOW + 30_000L, "first"));
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

        installed.get().publish(message(
                UUID.randomUUID(), UUID.randomUUID(), NOW, NOW + 30_000L, "second"));
        installed.get().publish(message(
                UUID.randomUUID(), UUID.randomUUID(), NOW, NOW + 30_000L, "dropped"));

        releaseFirst.countDown();
        assertTrue(secondDelivered.await(2, TimeUnit.SECONDS));
        Thread.sleep(50L);
        assertEquals(2, sends.get());
        integration.close();
    }

    @Test
    void toWirePreservesAllProviderNeutralFields() {
        UUID eventId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        OutboundChatMessage outbound = message(
                eventId, playerId, NOW, NOW + 25_000L, "plain");

        ChatBridgeOutboundMessage wire = RoseChatOutboundBridgeIntegration.toWire("HUB", outbound);

        assertEquals(eventId, wire.eventId());
        assertEquals("rosechat-mc-" + eventId, wire.externalMessageId());
        assertEquals("rosechat-canonical-" + eventId, wire.canonicalMessageId());
        assertEquals(NOW, wire.createdAtEpochMillis());
        assertEquals(NOW + 25_000L, wire.expiresAtEpochMillis());
        assertEquals("HUB", wire.sourceServerId());
        assertEquals("global", wire.logicalChannelId());
        assertEquals(playerId, wire.minecraftPlayerId());
        assertEquals("Player", wire.displayName());
        assertEquals("plain", wire.plainText());
    }

    private static RoseChatOutboundBridgeIntegration integration(
            AtomicReference<OutboundChatBridge> installed,
            AtomicBoolean registrationClosed,
            int queueCapacity
    ) {
        return new RoseChatOutboundBridgeIntegration(
                "SMP",
                CLOCK,
                bridge -> {
                    installed.set(bridge);
                    return () -> registrationClosed.set(true);
                },
                queueCapacity
        );
    }

    private static OutboundChatMessage message(
            UUID eventId,
            UUID playerId,
            long createdAt,
            long expiresAt,
            String text
    ) {
        return new OutboundChatMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                createdAt,
                expiresAt,
                "global",
                ChannelClassification.PUBLIC,
                OutboundChatMessage.Origin.MINECRAFT,
                playerId,
                "Player",
                text
        );
    }
}

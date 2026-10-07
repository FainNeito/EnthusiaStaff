package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class StaffBotChatIngressTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long CHANNEL_ID = 1541286004298752091L;
    private static final String SOURCE_SERVER = SOURCE_SERVER;
    private static final String LOGICAL_CHANNEL = LOGICAL_CHANNEL;
    private static final int FIRST_DELIVERY = 1;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final StaffBotChatBridgeConfiguration.Route ROUTE =
            new StaffBotChatBridgeConfiguration.Route(SOURCE_SERVER, LOGICAL_CHANNEL);

    @Test
    void requiresResumeAndExplicitRoute() throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(1);
        StaffBotChatIngress ingress = ingress((channelId, message) -> {
            deliveries.incrementAndGet();
            delivered.countDown();
            return true;
        });
        ChatBridgeOutboundMessage message = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        ProtocolEnvelope envelope = envelope(message);

        assertFalse(ingress.accept(envelope));

        ingress.resume();
        assertTrue(ingress.accept(envelope));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(1, deliveries.get());

        ChatBridgeOutboundMessage wrongRoute = message("HUB", LOGICAL_CHANNEL, NOW + 30_000L);
        assertFalse(ingress.accept(envelope(wrongRoute)));

        ChatBridgeOutboundMessage invalidRouteToken = message(SOURCE_SERVER, "global chat", NOW + 30_000L);
        assertFalse(ingress.accept(envelope(invalidRouteToken)));
        ingress.close();
    }

    @Test
    void rejectsWrongProxyIdentityEventIdentityMalformedAndExpired() {
        StaffBotChatIngress ingress = ingress((channelId, message) -> true);
        ingress.resume();

        ChatBridgeOutboundMessage valid = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        assertFalse(ingress.accept(envelope("OTHER", valid.eventId(), valid)));
        assertFalse(ingress.accept(envelope(
                StaffBotChatBridgeConfiguration.PROXY_ID, UUID.randomUUID(), valid)));
        assertFalse(ingress.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                StaffBotChatBridgeConfiguration.PROXY_ID,
                ChatBridgeMessages.OUTBOUND,
                NOW,
                "nonce",
                "{not-json}",
                "mac"
        )));

        StaffBotChatIngress expiredIngress = new StaffBotChatIngress(
                Map.of(ROUTE, CHANNEL_ID),
                8,
                32,
                Clock.fixed(Instant.ofEpochMilli(NOW + 30_001L), ZoneOffset.UTC),
                (channelId, message) -> true
        );
        expiredIngress.resume();
        ChatBridgeOutboundMessage expired = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        assertFalse(expiredIngress.accept(envelope(expired)));

        ingress.close();
        expiredIngress.close();
    }

    @Test
    void duplicateIsAckedWithoutSecondDelivery() throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(1);
        StaffBotChatIngress ingress = ingress((channelId, message) -> {
            deliveries.incrementAndGet();
            delivered.countDown();
            return true;
        });
        ingress.resume();
        ChatBridgeOutboundMessage message = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        ProtocolEnvelope envelope = envelope(message);

        assertTrue(ingress.accept(envelope));
        assertTrue(ingress.accept(envelope));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        Thread.sleep(50L);
        assertEquals(1, deliveries.get());

        ingress.close();
    }

    @Test
    @Timeout(5)
    void queueSaturationRejectsNewestWithoutBlocking() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDelivered = new CountDownLatch(1);
        AtomicInteger deliveries = new AtomicInteger();
        StaffBotChatIngress ingress = new StaffBotChatIngress(
                Map.of(ROUTE, CHANNEL_ID),
                1,
                32,
                CLOCK,
                (channelId, message) -> {
                    int sequence = deliveries.incrementAndGet();
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
                    return true;
                }
        );
        ingress.resume();

        ChatBridgeOutboundMessage first = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        ChatBridgeOutboundMessage second = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        ChatBridgeOutboundMessage third = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);

        assertTrue(ingress.accept(envelope(first)));
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        assertTrue(ingress.accept(envelope(second)));
        assertFalse(ingress.accept(envelope(third)));

        releaseFirst.countDown();
        assertTrue(secondDelivered.await(2, TimeUnit.SECONDS));
        assertEquals(2, deliveries.get());
        ingress.close();
    }

    @Test
    @Timeout(5)
    void pauseFencesQueuedWorkAcrossDiscordReconnect() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger deliveries = new AtomicInteger();
        StaffBotChatIngress ingress = ingress((channelId, message) -> {
            int sequence = deliveries.incrementAndGet();
            if (sequence == 1) {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        });
        ingress.resume();

        ChatBridgeOutboundMessage first = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        ChatBridgeOutboundMessage queued = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        assertTrue(ingress.accept(envelope(first)));
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
        assertTrue(ingress.accept(envelope(queued)));

        ingress.pause();
        releaseFirst.countDown();
        Thread.sleep(50L);
        assertEquals(1, deliveries.get());

        ingress.resume();
        ChatBridgeOutboundMessage afterReconnect = message(SOURCE_SERVER, LOGICAL_CHANNEL, NOW + 30_000L);
        assertTrue(ingress.accept(envelope(afterReconnect)));
        Thread.sleep(100L);
        assertEquals(2, deliveries.get());
        ingress.close();
    }

    private static StaffBotChatIngress ingress(DiscordChatEgress egress) {
        return new StaffBotChatIngress(Map.of(ROUTE, CHANNEL_ID), 8, 32, CLOCK, egress);
    }

    private static ChatBridgeOutboundMessage message(
            String sourceServer,
            String logicalChannel,
            long expiresAt
    ) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                expiresAt,
                sourceServer,
                logicalChannel,
                UUID.randomUUID(),
                "Player",
                "hello"
        );
    }

    private static ProtocolEnvelope envelope(ChatBridgeOutboundMessage message) {
        return envelope(StaffBotChatBridgeConfiguration.PROXY_ID, message.eventId(), message);
    }

    private static ProtocolEnvelope envelope(
            String serverId,
            UUID messageId,
            ChatBridgeOutboundMessage message
    ) {
        return new ProtocolEnvelope(
                1,
                messageId,
                serverId,
                ChatBridgeMessages.OUTBOUND,
                NOW,
                "nonce",
                ChatBridgeMessages.encodeOutbound(message),
                "mac"
        );
    }
}

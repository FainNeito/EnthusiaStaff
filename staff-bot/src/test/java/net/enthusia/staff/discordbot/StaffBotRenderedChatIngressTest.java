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
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class StaffBotRenderedChatIngressTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long CHANNEL_ID = 1541286004298752091L;
    private static final String SOURCE_SERVER = "SMP";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final StaffBotChatBridgeConfiguration.Route ROUTE =
            new StaffBotChatBridgeConfiguration.Route(SOURCE_SERVER, "global");

    @Test
    void requiresResumeAndRoutesStyledPayloadExactly() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        StaffBotRenderedChatIngress ingress = ingress((channelId, message) -> {
            assertEquals(CHANNEL_ID, channelId);
            assertTrue(message.lineAdventureJson().contains("#12ABEF"));
            sends.incrementAndGet();
            delivered.countDown();
            return true;
        });
        ChatBridgeRenderedMessage message = message(SOURCE_SERVER, "global", NOW + 30_000);
        ProtocolEnvelope envelope = envelope(message);

        assertFalse(ingress.accept(envelope));
        ingress.resume();
        assertTrue(ingress.accept(envelope));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(1, sends.get());

        assertFalse(ingress.accept(envelope(message("HUB", "global", NOW + 30_000))));
        ingress.close();
    }

    @Test
    void rejectsWrongProxyIdentityEventIdentityMalformedAndExpired() {
        StaffBotRenderedChatIngress ingress = ingress((channelId, message) -> true);
        ingress.resume();
        ChatBridgeRenderedMessage valid = message(SOURCE_SERVER, "global", NOW + 30_000);

        assertFalse(ingress.accept(new ProtocolEnvelope(
                1,
                valid.eventId(),
                "OTHER",
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(valid),
                "mac"
        )));
        assertFalse(ingress.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                StaffBotChatBridgeConfiguration.PROXY_ID,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(valid),
                "mac"
        )));
        assertFalse(ingress.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                StaffBotChatBridgeConfiguration.PROXY_ID,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                "{}",
                "mac"
        )));

        StaffBotRenderedChatIngress expired = new StaffBotRenderedChatIngress(
                Map.of(ROUTE, CHANNEL_ID),
                8,
                32,
                Clock.fixed(Instant.ofEpochMilli(NOW + 30_001), ZoneOffset.UTC),
                (channelId, message) -> true
        );
        expired.resume();
        assertFalse(expired.accept(envelope(message(SOURCE_SERVER, "global", NOW + 30_000))));

        ingress.close();
        expired.close();
    }

    @Test
    void duplicateIsAckedWithoutSecondDiscordDelivery() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(1);
        StaffBotRenderedChatIngress ingress = ingress((channelId, message) -> {
            sends.incrementAndGet();
            delivered.countDown();
            return true;
        });
        ingress.resume();
        ChatBridgeRenderedMessage message = message(SOURCE_SERVER, "global", NOW + 30_000);
        ProtocolEnvelope envelope = envelope(message);

        assertTrue(ingress.accept(envelope));
        assertTrue(ingress.accept(envelope));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        Thread.sleep(50L);
        assertEquals(1, sends.get());
        ingress.close();
    }

    private static StaffBotRenderedChatIngress ingress(DiscordRenderedChatEgress egress) {
        return new StaffBotRenderedChatIngress(
                Map.of(ROUTE, CHANNEL_ID),
                8,
                32,
                CLOCK,
                egress
        );
    }

    private static ProtocolEnvelope envelope(ChatBridgeRenderedMessage message) {
        return new ProtocolEnvelope(
                1,
                message.eventId(),
                StaffBotChatBridgeConfiguration.PROXY_ID,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(message),
                "mac"
        );
    }

    private static ChatBridgeRenderedMessage message(
            String sourceServer,
            String logicalChannel,
            long expiresAt
    ) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeRenderedMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                expiresAt,
                sourceServer,
                logicalChannel,
                UUID.randomUUID(),
                "Player",
                "hello",
                "hello",
                "**hello**",
                "{\"text\":\"hello\",\"color\":\"#12ABEF\"}",
                "[VIP] Player: hello",
                "**[VIP] Player:** hello",
                "{\"text\":\"[VIP] Player: hello\",\"color\":\"#12ABEF\"}"
        );
    }
}

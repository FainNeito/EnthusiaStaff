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
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class VelocityRenderedChatBridgeRelayTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void authenticatedPaperRenderUsesEphemeralSink() throws Exception {
        VelocityRenderedChatBridgeRelay relay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        AtomicReference<ChatBridgeRenderedMessage> delivered = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        relay.installSink(message -> {
            delivered.set(message);
            latch.countDown();
            return true;
        });
        ChatBridgeRenderedMessage message = message("SMP", NOW, NOW + 30_000);

        assertTrue(relay.accept(envelope("SMP", message)));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(message, delivered.get());
        relay.close();
    }

    @Test
    void rejectsIdentityMismatchExpiryAndMalformedPayload() {
        VelocityRenderedChatBridgeRelay relay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        relay.installSink(message -> true);

        ChatBridgeRenderedMessage valid = message("SMP", NOW, NOW + 30_000);
        assertFalse(relay.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                "SMP",
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(valid),
                "mac"
        )));
        assertFalse(relay.accept(envelope("HUB", valid)));
        ChatBridgeRenderedMessage expired = message("SMP", NOW - 30_000, NOW - 1);
        assertFalse(relay.accept(envelope("SMP", expired)));
        assertFalse(relay.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                "SMP",
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                "{}",
                "mac"
        )));
        relay.close();
    }

    @Test
    void duplicateAdmissionAcksWithoutDoubleDelivery() throws Exception {
        VelocityRenderedChatBridgeRelay relay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        relay.installSink(message -> {
            deliveries.incrementAndGet();
            first.countDown();
            return true;
        });
        ChatBridgeRenderedMessage message = message("SMP", NOW, NOW + 30_000);
        ProtocolEnvelope envelope = envelope("SMP", message);

        assertTrue(relay.accept(envelope));
        assertTrue(first.await(2, TimeUnit.SECONDS));
        assertTrue(relay.accept(envelope));
        Thread.sleep(50L);
        assertEquals(1, deliveries.get());
        relay.close();
    }

    private static ProtocolEnvelope envelope(
            String authenticatedServerId,
            ChatBridgeRenderedMessage message
    ) {
        return new ProtocolEnvelope(
                1,
                message.eventId(),
                authenticatedServerId,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(message),
                "mac"
        );
    }

    private static ChatBridgeRenderedMessage message(
            String sourceServerId,
            long createdAt,
            long expiresAt
    ) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeRenderedMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                createdAt,
                expiresAt,
                sourceServerId,
                "global",
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

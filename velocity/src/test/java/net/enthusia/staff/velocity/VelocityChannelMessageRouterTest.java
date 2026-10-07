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
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class VelocityChannelMessageRouterTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String PAPER_SERVER = "SMP";
    private static final String STAFF_BOT_PEER = "STAFFBOT";
    private static final String LOGICAL_CHANNEL = "global";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void authenticatedAuxiliaryPeerCannotReachPaperApplicationHandler() {
        AtomicInteger delegated = new AtomicInteger();
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                renderedRelay,
                inbound,
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );

        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                UUID.randomUUID(),
                "PUNISHMENT_CREATED",
                "{}"
        )));
        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                UUID.randomUUID(),
                "STAFF_MODE_READY",
                "{}"
        )));
        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                UUID.randomUUID(),
                "TRANSFER_SNAPSHOT",
                "{}"
        )));
        assertEquals(0, delegated.get());

        relay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void staffBotCannotInjectChatFrameEvenWhenRelaySinkExists() {
        AtomicInteger deliveries = new AtomicInteger();
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        relay.installSink(message -> {
            deliveries.incrementAndGet();
            return true;
        });
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                renderedRelay,
                inbound,
                envelope -> true
        );
        ChatBridgeOutboundMessage message = message(STAFF_BOT_PEER);

        assertFalse(router.handle(envelope(
                STAFF_BOT_PEER,
                message.eventId(),
                ChatBridgeMessages.OUTBOUND,
                ChatBridgeMessages.encodeOutbound(message)
        )));
        assertEquals(0, deliveries.get());

        relay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void paperChatUsesEphemeralRelayAndOtherFramesUseExistingHandler() throws Exception {
        AtomicInteger delegated = new AtomicInteger();
        CountDownLatch chatDelivered = new CountDownLatch(1);
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        relay.installSink(message -> {
            chatDelivered.countDown();
            return true;
        });
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                renderedRelay,
                inbound,
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );
        ChatBridgeOutboundMessage chat = message(PAPER_SERVER);

        assertTrue(router.handle(envelope(
                PAPER_SERVER,
                chat.eventId(),
                ChatBridgeMessages.OUTBOUND,
                ChatBridgeMessages.encodeOutbound(chat)
        )));
        assertTrue(chatDelivered.await(2, TimeUnit.SECONDS));
        assertEquals(0, delegated.get());

        assertTrue(router.handle(envelope(
                PAPER_SERVER,
                UUID.randomUUID(),
                "PUNISHMENT_CREATED",
                "{}"
        )));
        assertEquals(1, delegated.get());

        relay.close();
        renderedRelay.close();
        inbound.close();
    }

    @Test
    void renderedChatIsPaperOnlyAndNeverDelegatesToDurableHandler() throws Exception {
        AtomicInteger delegated = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(1);
        VelocityChatBridgeRelay relay = new VelocityChatBridgeRelay(CLOCK, 8, 32);
        VelocityRenderedChatBridgeRelay renderedRelay =
                new VelocityRenderedChatBridgeRelay(CLOCK, 8, 32);
        renderedRelay.installSink(message -> {
            delivered.countDown();
            return true;
        });
        VelocityDiscordChatIngressRelay inbound = new VelocityDiscordChatIngressRelay(
                Set.of(PAPER_SERVER), CLOCK, 8, 32);
        VelocityChannelMessageRouter router = new VelocityChannelMessageRouter(
                Set.of(PAPER_SERVER),
                relay,
                renderedRelay,
                inbound,
                envelope -> {
                    delegated.incrementAndGet();
                    return true;
                }
        );
        ChatBridgeRenderedMessage rendered = renderedMessage(PAPER_SERVER);

        assertTrue(router.handle(new ProtocolEnvelope(
                1,
                rendered.eventId(),
                PAPER_SERVER,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(rendered),
                "mac"
        )));
        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(0, delegated.get());

        assertFalse(router.handle(new ProtocolEnvelope(
                1,
                rendered.eventId(),
                STAFF_BOT_PEER,
                ChatRenderMessages.RENDERED,
                NOW,
                "nonce",
                ChatRenderMessages.encode(rendered),
                "mac"
        )));
        assertEquals(0, delegated.get());

        relay.close();
        renderedRelay.close();
        inbound.close();
    }

    private static ChatBridgeRenderedMessage renderedMessage(String sourceServerId) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeRenderedMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                NOW + 30_000L,
                sourceServerId,
                LOGICAL_CHANNEL,
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

    private static ChatBridgeOutboundMessage message(String sourceServerId) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                NOW + 30_000L,
                sourceServerId,
                LOGICAL_CHANNEL,
                UUID.randomUUID(),
                "Player",
                "hello"
        );
    }

    private static ProtocolEnvelope envelope(
            String serverId,
            UUID messageId,
            String messageType,
            String payload
    ) {
        return new ProtocolEnvelope(
                1,
                messageId,
                serverId,
                messageType,
                NOW,
                "nonce",
                payload,
                "mac"
        );
    }
}

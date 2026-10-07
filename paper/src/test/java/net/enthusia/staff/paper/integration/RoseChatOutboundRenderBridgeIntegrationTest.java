package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.chatbridge.OutboundChatMessage;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatRenderBridge;
import dev.rosewood.rosechat.api.chatbridge.RenderedOutboundChatMessage;
import dev.rosewood.rosechat.api.staff.ChannelClassification;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import org.junit.jupiter.api.Test;

class RoseChatOutboundRenderBridgeIntegrationTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void preservesStyledBodyAndFullLineOverDedicatedWireType() throws Exception {
        AtomicReference<OutboundChatRenderBridge> installed = new AtomicReference<>();
        AtomicBoolean registrationClosed = new AtomicBoolean();
        RoseChatOutboundRenderBridgeIntegration integration = new RoseChatOutboundRenderBridgeIntegration(
                "SMP",
                CLOCK,
                bridge -> {
                    installed.set(bridge);
                    return () -> registrationClosed.set(true);
                },
                8
        );
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> type = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        integration.bindChannelForTest(
                new Object(),
                () -> true,
                (id, messageType, json, timeout) -> {
                    type.set(messageType);
                    payload.set(json);
                    delivered.countDown();
                    return true;
                }
        );

        RenderedOutboundChatMessage rendered = rendered();
        installed.get().publish(rendered);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(ChatRenderMessages.RENDERED, type.get());
        ChatBridgeRenderedMessage decoded = ChatRenderMessages.decode(payload.get());
        assertEquals(rendered.message().eventId(), decoded.eventId());
        assertEquals("SMP", decoded.sourceServerId());
        assertEquals(rendered.bodyMarkdown(), decoded.bodyMarkdown());
        assertEquals(rendered.bodyAdventureJson(), decoded.bodyAdventureJson());
        assertEquals(rendered.lineMarkdown(), decoded.lineMarkdown());
        assertEquals(rendered.lineAdventureJson(), decoded.lineAdventureJson());

        integration.close();
        assertTrue(registrationClosed.get());
    }

    @Test
    void disconnectedTransportThrowsSoRoseChatCanUseV1Fallback() {
        AtomicReference<OutboundChatRenderBridge> installed = new AtomicReference<>();
        RoseChatOutboundRenderBridgeIntegration integration = new RoseChatOutboundRenderBridgeIntegration(
                "SMP",
                CLOCK,
                bridge -> {
                    installed.set(bridge);
                    return () -> { };
                },
                8
        );
        integration.bindChannelForTest(
                new Object(),
                () -> false,
                (id, type, json, timeout) -> true
        );

        assertThrows(IllegalStateException.class, () -> installed.get().publish(rendered()));
        integration.close();
    }

    @Test
    void toWirePreservesCanonicalIdentityAndResolvedRepresentations() {
        RenderedOutboundChatMessage rendered = rendered();

        ChatBridgeRenderedMessage wire =
                RoseChatOutboundRenderBridgeIntegration.toWire("HUB", rendered);

        assertEquals(rendered.message().eventId(), wire.eventId());
        assertEquals("HUB", wire.sourceServerId());
        assertEquals(rendered.message().plainText(), wire.canonicalPlainText());
        assertEquals(rendered.bodyPlainText(), wire.bodyPlainText());
        assertEquals(rendered.bodyMarkdown(), wire.bodyMarkdown());
        assertEquals(rendered.bodyAdventureJson(), wire.bodyAdventureJson());
        assertEquals(rendered.linePlainText(), wire.linePlainText());
        assertEquals(rendered.lineMarkdown(), wire.lineMarkdown());
        assertEquals(rendered.lineAdventureJson(), wire.lineAdventureJson());
    }

    private static RenderedOutboundChatMessage rendered() {
        UUID eventId = UUID.randomUUID();
        OutboundChatMessage base = new OutboundChatMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                NOW + 30_000,
                "global",
                ChannelClassification.PUBLIC,
                OutboundChatMessage.Origin.MINECRAFT,
                UUID.randomUUID(),
                "Player",
                "hello"
        );
        return new RenderedOutboundChatMessage(
                base,
                "hello",
                "**hello**",
                "{\"text\":\"hello\",\"color\":\"#12ABEF\"}",
                "[VIP] Player: hello",
                "**[VIP] Player:** hello",
                "{\"text\":\"[VIP] Player: hello\",\"color\":\"#12ABEF\"}"
        );
    }
}

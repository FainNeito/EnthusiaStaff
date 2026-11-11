package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRenderMessagesTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void roundTripPreservesStyledBodyAndLineMetadata() {
        ChatBridgeRenderedMessage message = message();

        String encoded = ChatRenderMessages.encode(message);
        ChatBridgeRenderedMessage decoded = ChatRenderMessages.decode(encoded);

        assertEquals(message, decoded);
        assertTrue(decoded.bodyAdventureJson().contains("#12ABEF"));
        assertFalse(decoded.isExpired(NOW));
        assertTrue(decoded.isExpired(NOW + 30_001));
    }

    @Test
    void rejectsUnknownFieldsAndUnsafeBounds() {
        ChatBridgeRenderedMessage message = message();
        String encoded = ChatRenderMessages.encode(message);
        String widened = encoded.substring(0, encoded.length() - 1) + ",\"unexpected\":true}";

        assertThrows(IllegalArgumentException.class, () -> ChatRenderMessages.decode(widened));
        assertThrows(IllegalArgumentException.class, () -> new ChatBridgeRenderedMessage(
                message.eventId(),
                message.externalMessageId(),
                message.canonicalMessageId(),
                message.createdAtEpochMillis(),
                message.expiresAtEpochMillis(),
                message.sourceServerId(),
                message.logicalChannelId(),
                message.minecraftPlayerId(),
                "Player\nSpoof",
                message.canonicalPlainText(),
                message.bodyPlainText(),
                message.bodyMarkdown(),
                message.bodyAdventureJson(),
                message.linePlainText(),
                message.lineMarkdown(),
                message.lineAdventureJson()
        ));
        assertThrows(IllegalArgumentException.class, () -> new ChatBridgeRenderedMessage(
                message.eventId(),
                message.externalMessageId(),
                message.canonicalMessageId(),
                message.createdAtEpochMillis(),
                message.createdAtEpochMillis() + ChatBridgeRenderedMessage.MAX_LIFETIME_MILLIS + 1,
                message.sourceServerId(),
                message.logicalChannelId(),
                message.minecraftPlayerId(),
                message.displayName(),
                message.canonicalPlainText(),
                message.bodyPlainText(),
                message.bodyMarkdown(),
                message.bodyAdventureJson(),
                message.linePlainText(),
                message.lineMarkdown(),
                message.lineAdventureJson()
        ));
    }

    @Test
    void dedicatedPayloadBoundRunsBeforeJsonParsing() {
        String oversized = "{\"value\":\"" + "x".repeat(ChatRenderMessages.MAX_PAYLOAD_BYTES) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> ChatRenderMessages.decode(oversized));
    }

    private static ChatBridgeRenderedMessage message() {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeRenderedMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                NOW,
                NOW + 30_000,
                "SMP",
                "global",
                UUID.randomUUID(),
                "Player",
                "hello",
                "hello",
                "**hello**",
                "{\"text\":\"hello\",\"color\":\"#12ABEF\"}",
                "[Member] Player: hello",
                "**[Member] Player:** **hello**",
                "{\"text\":\"[Member] Player: hello\",\"color\":\"#12ABEF\"}"
        );
    }
}

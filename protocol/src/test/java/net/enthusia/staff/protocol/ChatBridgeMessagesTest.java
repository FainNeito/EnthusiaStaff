package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatBridgeMessagesTest {

    private static final String HELLO = HELLO;

    @Test
    void roundTripPreservesBoundedPublicChatEnvelope() {
        ChatBridgeOutboundMessage message = message(HELLO, 30_000L);

        String encoded = ChatBridgeMessages.encodeOutbound(message);
        ChatBridgeOutboundMessage decoded = ChatBridgeMessages.decodeOutbound(encoded);

        assertEquals(message, decoded);
        assertFalse(decoded.isExpired(decoded.createdAtEpochMillis()));
        assertTrue(decoded.isExpired(decoded.expiresAtEpochMillis() + 1));
    }

    @Test
    void rejectsUnknownFieldsInsteadOfSilentlyWideningContract() {
        ChatBridgeOutboundMessage message = message(HELLO, 30_000L);
        String encoded = ChatBridgeMessages.encodeOutbound(message);
        String widened = encoded.substring(0, encoded.length() - 1) + ",\"unexpected\":true}";

        assertThrows(IllegalArgumentException.class, () -> ChatBridgeMessages.decodeOutbound(widened));
    }

    @Test
    void rejectsOversizeTextAndUnboundedLifetime() {
        assertThrows(IllegalArgumentException.class,
                () -> message("x".repeat(ChatBridgeOutboundMessage.MAX_PLAIN_TEXT_LENGTH + 1), 30_000L));
        assertThrows(IllegalArgumentException.class,
                () -> message(HELLO, ChatBridgeOutboundMessage.MAX_LIFETIME_MILLIS + 1));
    }

    @Test
    void rejectsControlCharactersInRoutingAndPresentationFields() {
        ChatBridgeOutboundMessage valid = message(HELLO, 30_000L);

        assertThrows(IllegalArgumentException.class, () -> new ChatBridgeOutboundMessage(
                valid.eventId(),
                valid.externalMessageId(),
                valid.canonicalMessageId(),
                valid.createdAtEpochMillis(),
                valid.expiresAtEpochMillis(),
                "smp\nspoof",
                valid.logicalChannelId(),
                valid.minecraftPlayerId(),
                valid.displayName(),
                valid.plainText()
        ));
        assertThrows(IllegalArgumentException.class, () -> new ChatBridgeOutboundMessage(
                valid.eventId(),
                valid.externalMessageId(),
                valid.canonicalMessageId(),
                valid.createdAtEpochMillis(),
                valid.expiresAtEpochMillis(),
                valid.sourceServerId(),
                valid.logicalChannelId(),
                valid.minecraftPlayerId(),
                "Player\nSpoof",
                valid.plainText()
        ));
    }

    @Test
    void payloadSizeGuardRunsBeforeJsonParsing() {
        String oversized = "{\"value\":\"" + "x".repeat(ChatBridgeMessages.MAX_PAYLOAD_BYTES) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> ChatBridgeMessages.decodeOutbound(oversized));
    }

    private static ChatBridgeOutboundMessage message(String text, long lifetimeMillis) {
        long createdAt = 1_800_000_000_000L;
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                createdAt,
                createdAt + lifetimeMillis,
                "smp",
                "global",
                UUID.randomUUID(),
                "Player",
                text
        );
    }
}

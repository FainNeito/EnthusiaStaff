package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatArtifactMessagesTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void roundTripPreservesArtifactBytesAndMetadata() {
        ChatBridgeArtifactBundle bundle = bundle(List.of(artifact(16_384, "Item.png")));

        String encoded = ChatArtifactMessages.encode(bundle);
        ChatBridgeArtifactBundle decoded = ChatArtifactMessages.decode(encoded);

        assertEquals(bundle.eventId(), decoded.eventId());
        assertEquals("SMP", decoded.sourceServerId());
        assertEquals("global", decoded.logicalChannelId());
        assertEquals(1, decoded.artifacts().size());
        assertEquals(ChatBridgeArtifact.Kind.ITEM, decoded.artifacts().getFirst().kind());
        assertArrayEquals(bundle.artifacts().getFirst().data(), decoded.artifacts().getFirst().data());
        assertTrue(encoded.length() < ChatArtifactMessages.MAX_PAYLOAD_BYTES);
    }

    @Test
    void artifactTransportIdentityIsStableAndDistinctFromEventIdentity() {
        UUID eventId = UUID.randomUUID();

        UUID first = ChatArtifactMessages.transportMessageId(eventId);
        UUID second = ChatArtifactMessages.transportMessageId(eventId);

        assertEquals(first, second);
        org.junit.jupiter.api.Assertions.assertNotEquals(eventId, first);
    }

    @Test
    void rejectsUnknownFieldsAndUnsafeArtifactMetadata() {
        ChatBridgeArtifactBundle bundle = bundle(List.of(artifact(8, "Item.png")));
        String encoded = ChatArtifactMessages.encode(bundle);
        String widened = encoded.substring(0, encoded.length() - 1) + ",\"unexpected\":true}";

        assertThrows(IllegalArgumentException.class, () -> ChatArtifactMessages.decode(widened));
        assertThrows(IllegalArgumentException.class, () -> new ChatBridgeArtifact(
                ChatBridgeArtifact.Kind.ITEM,
                0,
                "../Item.png",
                "image/png",
                "item",
                new byte[] {1}
        ));
        assertThrows(IllegalArgumentException.class, () -> new ChatBridgeArtifact(
                ChatBridgeArtifact.Kind.ITEM,
                0,
                "Item.png",
                "image/jpeg",
                "item",
                new byte[] {1}
        ));
    }

    @Test
    void enforcesPerArtifactAndAggregateBounds() {
        assertThrows(IllegalArgumentException.class, () -> artifact(
                ChatBridgeArtifact.MAX_ARTIFACT_BYTES + 1,
                "TooLarge.png"
        ));

        ChatBridgeArtifact first = artifact(230_000, "First.png");
        ChatBridgeArtifact second = artifact(230_000, "Second.png");
        assertThrows(IllegalArgumentException.class, () -> bundle(List.of(first, second)));

        ChatBridgeArtifactBundle maximumUseful = bundle(List.of(
                artifact(220_000, "Inventory.png"),
                artifact(220_000, "Tooltip.png")
        ));
        String encoded = ChatArtifactMessages.encode(maximumUseful);
        assertTrue(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                < ChatArtifactMessages.MAX_PAYLOAD_BYTES);
    }

    @Test
    void payloadGuardRunsBeforeJsonParsing() {
        String oversized = "{\"value\":\"" + "x".repeat(ChatArtifactMessages.MAX_PAYLOAD_BYTES) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> ChatArtifactMessages.decode(oversized));
    }

    private static ChatBridgeArtifact artifact(int size, String filename) {
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }
        return new ChatBridgeArtifact(
                ChatBridgeArtifact.Kind.ITEM,
                4,
                filename,
                "image/png",
                "Shared item",
                data
        );
    }

    private static ChatBridgeArtifactBundle bundle(List<ChatBridgeArtifact> artifacts) {
        return new ChatBridgeArtifactBundle(
                UUID.randomUUID(),
                NOW,
                NOW + 30_000,
                "SMP",
                "global",
                artifacts
        );
    }
}

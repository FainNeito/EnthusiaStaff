package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import org.junit.jupiter.api.Test;

class StaffBotChatArtifactStoreTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void consumesOnlyMatchingEventSourceAndChannel() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle bundle = bundle("SMP", "global", NOW + 30_000);

        assertTrue(store.put(bundle, NOW));
        assertEquals(1, store.size());
        assertTrue(store.consume(bundle.eventId(), "HUB", "global", NOW).isEmpty());
        assertEquals(0, store.size());

        assertTrue(store.put(bundle, NOW));
        assertEquals(1, store.consume(bundle.eventId(), "SMP", "global", NOW).size());
        assertEquals(0, store.size());
        assertEquals(0L, store.storedBytes());
    }

    @Test
    void expiryAndClearRemoveEphemeralBytes() {
        StaffBotChatArtifactStore store = new StaffBotChatArtifactStore();
        ChatBridgeArtifactBundle bundle = bundle("SMP", "global", NOW + 30_000);
        assertTrue(store.put(bundle, NOW));
        assertTrue(store.storedBytes() > 0);

        assertTrue(store.consume(bundle.eventId(), "SMP", "global", NOW + 30_001).isEmpty());
        assertEquals(0L, store.storedBytes());

        assertTrue(store.put(bundle, NOW));
        store.clear();
        assertEquals(0, store.size());
        assertEquals(0L, store.storedBytes());
    }

    private static ChatBridgeArtifactBundle bundle(
            String sourceServer,
            String logicalChannel,
            long expiresAt
    ) {
        return new ChatBridgeArtifactBundle(
                UUID.randomUUID(),
                NOW,
                expiresAt,
                sourceServer,
                logicalChannel,
                List.of(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.ITEM,
                        3,
                        "Item.png",
                        "image/png",
                        "Shared item",
                        new byte[] {1, 2, 3, 4}
                ))
        );
    }
}

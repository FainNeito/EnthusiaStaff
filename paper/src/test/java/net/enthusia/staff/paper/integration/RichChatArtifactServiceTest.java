package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.enthusia.staff.api.chat.RichChatArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import org.junit.jupiter.api.Test;

class RichChatArtifactServiceTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void convertsProviderPngsIntoBoundedWireBundle() {
        RichChatArtifactService service = RichChatArtifactService.forTest(
                () -> request -> CompletableFuture.completedFuture(List.of(
                        new RichChatArtifact(
                                RichChatArtifact.Kind.ITEM,
                                4,
                                "Item.png",
                                "image/png",
                                "Shared item",
                                new byte[] {1, 2, 3, 4}
                        )
                )),
                CLOCK
        );

        ChatBridgeArtifactBundle bundle = service.render("SMP", message()).orElseThrow();

        assertEquals("SMP", bundle.sourceServerId());
        assertEquals("global", bundle.logicalChannelId());
        assertEquals(1, bundle.artifacts().size());
        assertEquals("Item.png", bundle.artifacts().getFirst().filename());
        assertEquals(4, bundle.artifacts().getFirst().position());
    }

    @Test
    void absentFailedAndOversizedProvidersDegradeToNoArtifacts() {
        RichChatArtifactService absent = RichChatArtifactService.forTest(() -> null, CLOCK);
        assertTrue(absent.render("SMP", message()).isEmpty());

        RichChatArtifactService failed = RichChatArtifactService.forTest(
                () -> request -> CompletableFuture.failedFuture(
                        new IllegalStateException("renderer unavailable")),
                CLOCK
        );
        assertTrue(failed.render("SMP", message()).isEmpty());

        RichChatArtifactService oversized = RichChatArtifactService.forTest(
                () -> request -> CompletableFuture.completedFuture(List.of(
                        artifact(230_000, "One.png"),
                        artifact(230_000, "Two.png")
                )),
                CLOCK
        );
        assertTrue(oversized.render("SMP", message()).isEmpty());
    }

    @Test
    void providerRequestUsesCanonicalEventIdentity() {
        UUID eventId = UUID.randomUUID();
        ChatBridgeRenderedMessage message = message(eventId);
        RichChatArtifactService service = RichChatArtifactService.forTest(
                () -> request -> {
                    assertEquals(eventId, request.eventId());
                    assertEquals(message.minecraftPlayerId(), request.minecraftPlayerId());
                    assertEquals(message.displayName(), request.displayName());
                    assertEquals(message.logicalChannelId(), request.logicalChannelId());
                    assertEquals(message.canonicalPlainText(), request.canonicalPlainText());
                    return CompletableFuture.completedFuture(List.of(
                            artifact(8, "Observed.png")
                    ));
                },
                CLOCK
        );

        assertTrue(service.render("SMP", message).isPresent());
    }

    private static RichChatArtifact artifact(int size, String filename) {
        return new RichChatArtifact(
                RichChatArtifact.Kind.INVENTORY,
                7,
                filename,
                "image/png",
                "Shared inventory",
                new byte[size]
        );
    }

    private static ChatBridgeRenderedMessage message() {
        return message(UUID.randomUUID());
    }

    private static ChatBridgeRenderedMessage message(UUID eventId) {
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
                "[VIP] Player: hello",
                "**[VIP] Player:** hello",
                "{\"text\":\"[VIP] Player: hello\",\"color\":\"#12ABEF\"}"
        );
    }
}

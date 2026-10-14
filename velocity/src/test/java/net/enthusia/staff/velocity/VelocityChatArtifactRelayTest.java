package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.protocol.ChatArtifactMessages;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

class VelocityChatArtifactRelayTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Test
    void ackWaitsForSinkAndDuplicateDoesNotRedeliver() {
        VelocityChatArtifactRelay relay = new VelocityChatArtifactRelay(CLOCK, 32);
        AtomicInteger deliveries = new AtomicInteger();
        relay.installSink(bundle -> {
            deliveries.incrementAndGet();
            return true;
        });
        ChatBridgeArtifactBundle bundle = bundle("SMP", NOW + 30_000);
        ProtocolEnvelope envelope = envelope("SMP", bundle);

        assertTrue(relay.accept(envelope));
        assertEquals(1, deliveries.get());
        assertTrue(relay.accept(envelope));
        assertEquals(1, deliveries.get());
        relay.close();
    }

    @Test
    void failedSinkReleasesReservationForImmediateRetry() {
        VelocityChatArtifactRelay relay = new VelocityChatArtifactRelay(CLOCK, 32);
        AtomicInteger attempts = new AtomicInteger();
        relay.installSink(bundle -> attempts.incrementAndGet() > 1);
        ChatBridgeArtifactBundle bundle = bundle("SMP", NOW + 30_000);
        ProtocolEnvelope envelope = envelope("SMP", bundle);

        assertFalse(relay.accept(envelope));
        assertTrue(relay.accept(envelope));
        assertEquals(2, attempts.get());
        relay.close();
    }

    @Test
    void rejectsWrongSourceTransportIdentityExpiryAndMalformedPayload() {
        VelocityChatArtifactRelay relay = new VelocityChatArtifactRelay(CLOCK, 32);
        relay.installSink(bundle -> true);
        ChatBridgeArtifactBundle valid = bundle("SMP", NOW + 30_000);

        assertFalse(relay.accept(envelope("HUB", valid)));
        assertFalse(relay.accept(new ProtocolEnvelope(
                1,
                valid.eventId(),
                "SMP",
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                ChatArtifactMessages.encode(valid),
                "mac"
        )));
        assertFalse(relay.accept(envelope("SMP", bundle("SMP", NOW - 1))));
        assertFalse(relay.accept(new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                "SMP",
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                "{}",
                "mac"
        )));
        relay.close();
    }

    private static ProtocolEnvelope envelope(
            String authenticatedServer,
            ChatBridgeArtifactBundle bundle
    ) {
        return new ProtocolEnvelope(
                1,
                ChatArtifactMessages.transportMessageId(bundle.eventId()),
                authenticatedServer,
                ChatArtifactMessages.ARTIFACTS,
                NOW,
                "nonce",
                ChatArtifactMessages.encode(bundle),
                "mac"
        );
    }

    private static ChatBridgeArtifactBundle bundle(String sourceServer, long expiresAt) {
        return new ChatBridgeArtifactBundle(
                UUID.randomUUID(),
                Math.min(NOW, expiresAt - 1),
                expiresAt,
                sourceServer,
                "global",
                List.of(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.INVENTORY,
                        5,
                        "Inventory.png",
                        "image/png",
                        "Shared inventory",
                        new byte[] {1, 2, 3, 4}
                ))
        );
    }
}

package net.enthusia.staff.velocity;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatArtifactMessages;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/**
 * Ephemeral Velocity admission boundary for rich Minecraft chat artifact bundles.
 *
 * <p>Unlike ordinary chat relays, artifact admission waits for the StaffBot sink result before
 * returning. The Paper sender therefore receives an ACK only after StaffBot has cached the bundle,
 * which guarantees the later styled frame cannot overtake its attachments.</p>
 */
final class VelocityChatArtifactRelay implements AutoCloseable {
    static final int DEFAULT_DEDUPE_CAPACITY = 1_024;

    @FunctionalInterface
    interface Sink {
        boolean offer(ChatBridgeArtifactBundle bundle);
    }

    @FunctionalInterface
    interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private record SinkSlot(Sink sink) {
        private SinkSlot {
            Objects.requireNonNull(sink, "sink");
        }
    }

    private record Admission(boolean accepted, boolean duplicate, long expiresAt) {
    }

    private final Clock clock;
    private final int maximumDedupeEntries;
    private final Map<UUID, Long> dedupeUntil = new ConcurrentHashMap<>();
    private final Object dedupeLock = new Object();
    private final AtomicReference<SinkSlot> sink = new AtomicReference<>();

    VelocityChatArtifactRelay(Clock clock) {
        this(clock, DEFAULT_DEDUPE_CAPACITY);
    }

    VelocityChatArtifactRelay(Clock clock, int maximumDedupeEntries) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumDedupeEntries < 1) {
            throw new IllegalArgumentException("chat artifact relay dedupe bound must be positive");
        }
        this.maximumDedupeEntries = maximumDedupeEntries;
    }

    boolean handles(ProtocolEnvelope envelope) {
        return envelope != null && ChatArtifactMessages.ARTIFACTS.equals(envelope.messageType());
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!handles(envelope)) {
            return false;
        }
        Optional<ChatBridgeArtifactBundle> decoded = decode(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeArtifactBundle bundle = decoded.orElseThrow();
        long now = clock.millis();
        if (!bundle.sourceServerId().equals(envelope.serverId())
                || !ChatArtifactMessages.transportMessageId(bundle.eventId())
                        .equals(envelope.messageId())
                || bundle.isExpired(now)) {
            return false;
        }
        return deliver(bundle, now);
    }

    Registration installSink(Sink newSink) {
        SinkSlot slot = new SinkSlot(Objects.requireNonNull(newSink, "newSink"));
        if (!sink.compareAndSet(null, slot)) {
            throw new IllegalStateException("chat artifact relay sink is already installed");
        }
        return () -> sink.compareAndSet(slot, null);
    }

    private boolean deliver(ChatBridgeArtifactBundle bundle, long now) {
        SinkSlot currentSink = sink.get();
        if (currentSink == null) {
            return false;
        }
        Admission admission = reserve(bundle, now);
        if (!admission.accepted()) {
            return admission.duplicate();
        }

        boolean delivered;
        try {
            delivered = currentSink.sink().offer(bundle);
        } catch (RuntimeException failure) {
            delivered = false;
        }
        if (!delivered) {
            release(bundle.eventId(), admission.expiresAt());
        }
        return delivered;
    }

    private static Optional<ChatBridgeArtifactBundle> decode(String payloadJson) {
        try {
            return Optional.of(ChatArtifactMessages.decode(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private Admission reserve(ChatBridgeArtifactBundle bundle, long now) {
        synchronized (dedupeLock) {
            Long existing = dedupeUntil.get(bundle.eventId());
            if (existing != null && existing >= now) {
                return new Admission(false, true, existing);
            }
            if (existing != null) {
                dedupeUntil.remove(bundle.eventId());
            }
            dedupeUntil.entrySet().removeIf(entry -> entry.getValue() < now);
            if (dedupeUntil.size() >= maximumDedupeEntries) {
                return new Admission(false, false, -1L);
            }
            long expiresAt = bundle.expiresAtEpochMillis();
            dedupeUntil.put(bundle.eventId(), expiresAt);
            return new Admission(true, false, expiresAt);
        }
    }

    private void release(UUID eventId, long expiresAt) {
        synchronized (dedupeLock) {
            dedupeUntil.remove(eventId, expiresAt);
        }
    }

    @Override
    public void close() {
        sink.set(null);
        synchronized (dedupeLock) {
            dedupeUntil.clear();
        }
    }
}

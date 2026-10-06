package net.enthusia.staff.velocity;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/**
 * Ephemeral Velocity admission boundary for Minecraft-to-Discord chat.
 *
 * <p>Accepted frames are kept only in a bounded in-memory queue. They are never written to the
 * durable network inbox/outbox. A duplicate already admitted during its short lifetime is ACKed
 * again without being delivered twice.</p>
 */
final class VelocityChatBridgeRelay implements AutoCloseable {
    static final int DEFAULT_QUEUE_CAPACITY = 256;
    static final int DEFAULT_DEDUPE_CAPACITY = 4_096;

    @FunctionalInterface
    interface Sink {
        boolean offer(ChatBridgeOutboundMessage message);
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

    private record Admission(boolean accepted, long expiresAt) {
    }

    private final Clock clock;
    private final int maximumDedupeEntries;
    private final Map<UUID, Long> dedupeUntil = new HashMap<>();
    private final Object dedupeLock = new Object();
    private final AtomicReference<SinkSlot> sink = new AtomicReference<>();
    private final ThreadPoolExecutor worker;

    VelocityChatBridgeRelay(Clock clock) {
        this(clock, DEFAULT_QUEUE_CAPACITY, DEFAULT_DEDUPE_CAPACITY);
    }

    VelocityChatBridgeRelay(Clock clock, int queueCapacity, int maximumDedupeEntries) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (queueCapacity < 1 || maximumDedupeEntries < 1) {
            throw new IllegalArgumentException("chat relay bounds must be positive");
        }
        this.maximumDedupeEntries = maximumDedupeEntries;
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-Velocity-Chat-Relay");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    boolean handles(ProtocolEnvelope envelope) {
        return envelope != null && ChatBridgeMessages.OUTBOUND.equals(envelope.messageType());
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!handles(envelope)) {
            return false;
        }

        final ChatBridgeOutboundMessage message;
        try {
            message = ChatBridgeMessages.decodeOutbound(envelope.payloadJson());
        } catch (IllegalArgumentException failure) {
            return false;
        }
        long now = clock.millis();
        if (!message.sourceServerId().equals(envelope.serverId()) || message.isExpired(now)) {
            return false;
        }

        SinkSlot currentSink = sink.get();
        if (currentSink == null) {
            return false;
        }

        Admission admission = reserve(message, now);
        if (!admission.accepted()) {
            return admission.expiresAt() >= 0L;
        }

        try {
            worker.execute(() -> deliver(currentSink, message));
            return true;
        } catch (RejectedExecutionException failure) {
            release(message.eventId(), admission.expiresAt());
            return false;
        }
    }

    Registration installSink(Sink newSink) {
        SinkSlot slot = new SinkSlot(newSink);
        if (!sink.compareAndSet(null, slot)) {
            throw new IllegalStateException("chat relay sink is already installed");
        }
        return () -> sink.compareAndSet(slot, null);
    }

    private Admission reserve(ChatBridgeOutboundMessage message, long now) {
        synchronized (dedupeLock) {
            Long existing = dedupeUntil.get(message.eventId());
            if (existing != null && existing >= now) {
                return new Admission(false, existing);
            }
            if (existing != null) {
                dedupeUntil.remove(message.eventId());
            }
            dedupeUntil.entrySet().removeIf(entry -> entry.getValue() < now);
            if (dedupeUntil.size() >= maximumDedupeEntries) {
                return new Admission(false, -1L);
            }
            long expiresAt = message.expiresAtEpochMillis();
            dedupeUntil.put(message.eventId(), expiresAt);
            return new Admission(true, expiresAt);
        }
    }

    private void release(UUID eventId, long expiresAt) {
        synchronized (dedupeLock) {
            dedupeUntil.remove(eventId, expiresAt);
        }
    }

    private void deliver(SinkSlot expectedSink, ChatBridgeOutboundMessage message) {
        if (sink.get() != expectedSink || message.isExpired(clock.millis())) {
            return;
        }
        try {
            expectedSink.sink().offer(message);
        } catch (RuntimeException ignored) {
            // Chat is best-effort. Sink failures never escape the relay worker.
        }
    }

    @Override
    public void close() {
        sink.set(null);
        worker.shutdownNow();
        synchronized (dedupeLock) {
            dedupeUntil.clear();
        }
    }
}

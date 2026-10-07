package net.enthusia.staff.velocity;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/**
 * Ephemeral Velocity admission boundary for styled Minecraft-to-Discord chat.
 */
final class VelocityRenderedChatBridgeRelay implements AutoCloseable {
    static final int DEFAULT_QUEUE_CAPACITY = 64;
    static final int DEFAULT_DEDUPE_CAPACITY = 4_096;

    @FunctionalInterface
    interface Sink {
        boolean offer(ChatBridgeRenderedMessage message);
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
    private final ThreadPoolExecutor worker;

    VelocityRenderedChatBridgeRelay(Clock clock) {
        this(clock, DEFAULT_QUEUE_CAPACITY, DEFAULT_DEDUPE_CAPACITY);
    }

    VelocityRenderedChatBridgeRelay(Clock clock, int queueCapacity, int maximumDedupeEntries) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (queueCapacity < 1 || maximumDedupeEntries < 1) {
            throw new IllegalArgumentException("styled chat relay bounds must be positive");
        }
        this.maximumDedupeEntries = maximumDedupeEntries;
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-Velocity-Styled-Chat");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    boolean handles(ProtocolEnvelope envelope) {
        return envelope != null && ChatRenderMessages.RENDERED.equals(envelope.messageType());
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!handles(envelope)) {
            return false;
        }
        Optional<ChatBridgeRenderedMessage> decoded = decode(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeRenderedMessage message = decoded.orElseThrow();
        long now = clock.millis();
        if (!message.sourceServerId().equals(envelope.serverId())
                || !message.eventId().equals(envelope.messageId())
                || message.isExpired(now)) {
            return false;
        }
        return admit(message, now);
    }

    Registration installSink(Sink newSink) {
        SinkSlot slot = new SinkSlot(Objects.requireNonNull(newSink, "newSink"));
        if (!sink.compareAndSet(null, slot)) {
            throw new IllegalStateException("styled chat relay sink is already installed");
        }
        return () -> sink.compareAndSet(slot, null);
    }

    private boolean admit(ChatBridgeRenderedMessage message, long now) {
        SinkSlot currentSink = sink.get();
        if (currentSink == null) {
            return false;
        }
        Admission admission = reserve(message, now);
        if (!admission.accepted()) {
            return admission.duplicate();
        }
        try {
            worker.execute(() -> deliver(currentSink, message));
            return true;
        } catch (RejectedExecutionException failure) {
            release(message.eventId(), admission.expiresAt());
            return false;
        }
    }

    private static Optional<ChatBridgeRenderedMessage> decode(String payloadJson) {
        try {
            return Optional.of(ChatRenderMessages.decode(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private Admission reserve(ChatBridgeRenderedMessage message, long now) {
        synchronized (dedupeLock) {
            Long existing = dedupeUntil.get(message.eventId());
            if (existing != null && existing >= now) {
                return new Admission(false, true, existing);
            }
            if (existing != null) {
                dedupeUntil.remove(message.eventId());
            }
            dedupeUntil.entrySet().removeIf(entry -> entry.getValue() < now);
            if (dedupeUntil.size() >= maximumDedupeEntries) {
                return new Admission(false, false, -1L);
            }
            long expiresAt = message.expiresAtEpochMillis();
            dedupeUntil.put(message.eventId(), expiresAt);
            return new Admission(true, false, expiresAt);
        }
    }

    private void release(UUID eventId, long expiresAt) {
        synchronized (dedupeLock) {
            dedupeUntil.remove(eventId, expiresAt);
        }
    }

    private void deliver(SinkSlot expectedSink, ChatBridgeRenderedMessage message) {
        if (sink.get() != expectedSink || message.isExpired(clock.millis())) {
            return;
        }
        try {
            expectedSink.sink().offer(message);
        } catch (RuntimeException ignored) {
            // Styled chat is ephemeral; sink failures never escape the relay worker.
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

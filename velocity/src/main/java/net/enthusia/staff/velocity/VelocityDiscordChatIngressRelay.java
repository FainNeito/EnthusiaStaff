package net.enthusia.staff.velocity;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeInboundMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/**
 * Ephemeral authenticated StaffBot -> Paper chat relay.
 *
 * <p>Only the application-authorized StaffBot peer may enter this relay. Messages are decoded,
 * expiry/dedupe checked, and constrained to configured Paper backend IDs before being admitted
 * to a bounded in-memory worker queue. Nothing is written to the durable network outbox.</p>
 */
final class VelocityDiscordChatIngressRelay implements AutoCloseable {
    static final int DEFAULT_QUEUE_CAPACITY = 256;
    static final int DEFAULT_DEDUPE_CAPACITY = 4_096;
    static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    @FunctionalInterface
    interface Sender {
        PersistentChannelServer.DeliveryStatus send(
                String peerId,
                UUID messageId,
                String messageType,
                String payloadJson,
                Duration timeout
        );
    }

    private record Binding(Object identity, Sender sender) {
        private Binding {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(sender, "sender");
        }
    }

    private record Admission(boolean accepted, boolean duplicate, long expiresAt) {
    }

    private final Set<String> paperBackendIds;
    private final Clock clock;
    private final int maximumDedupeEntries;
    private final Map<UUID, Long> dedupeUntil = new ConcurrentHashMap<>();
    private final Object dedupeLock = new Object();
    private final AtomicReference<Binding> binding = new AtomicReference<>();
    private final ThreadPoolExecutor worker;

    VelocityDiscordChatIngressRelay(Set<String> paperBackendIds, Clock clock) {
        this(paperBackendIds, clock, DEFAULT_QUEUE_CAPACITY, DEFAULT_DEDUPE_CAPACITY);
    }

    VelocityDiscordChatIngressRelay(
            Set<String> paperBackendIds,
            Clock clock,
            int queueCapacity,
            int maximumDedupeEntries
    ) {
        this.paperBackendIds = Set.copyOf(Objects.requireNonNull(paperBackendIds, "paperBackendIds"));
        this.clock = Objects.requireNonNull(clock, "clock");
        if (this.paperBackendIds.isEmpty() || queueCapacity < 1 || maximumDedupeEntries < 1) {
            throw new IllegalArgumentException("Discord chat ingress relay bounds/backends are invalid");
        }
        this.maximumDedupeEntries = maximumDedupeEntries;
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-Velocity-Discord-Ingress");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    boolean handles(ProtocolEnvelope envelope) {
        return envelope != null && ChatBridgeMessages.INBOUND.equals(envelope.messageType());
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!handles(envelope)) {
            return false;
        }
        Optional<ChatBridgeInboundMessage> decoded = decode(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeInboundMessage message = decoded.orElseThrow();
        long now = clock.millis();
        if (!message.eventId().equals(envelope.messageId())
                || message.isExpired(now)
                || !paperBackendIds.contains(message.targetServerId())) {
            return false;
        }
        return admit(message, now);
    }

    void bind(PersistentChannelServer channel) {
        Objects.requireNonNull(channel, "channel");
        binding.set(new Binding(
                channel,
                (peerId, messageId, messageType, payloadJson, timeout) ->
                        channel.send(peerId, messageId, messageType, payloadJson, timeout).join()
        ));
    }

    void unbind(PersistentChannelServer channel) {
        if (channel == null) {
            return;
        }
        Binding current = binding.get();
        if (current != null && current.identity() == channel) {
            binding.compareAndSet(current, null);
        }
    }

    void bindForTest(Object identity, Sender sender) {
        binding.set(new Binding(identity, sender));
    }

    private boolean admit(ChatBridgeInboundMessage message, long now) {
        Binding current = binding.get();
        if (current == null) {
            return false;
        }
        Admission admission = reserve(message, now);
        if (!admission.accepted()) {
            return admission.duplicate();
        }
        try {
            String payload = ChatBridgeMessages.encodeInbound(message);
            worker.execute(() -> deliver(current, message, payload));
            return true;
        } catch (IllegalArgumentException | RejectedExecutionException failure) {
            release(message.eventId(), admission.expiresAt());
            return false;
        }
    }

    private void deliver(
            Binding expected,
            ChatBridgeInboundMessage message,
            String payload
    ) {
        if (binding.get() != expected || message.isExpired(clock.millis())) {
            return;
        }
        try {
            expected.sender().send(
                    message.targetServerId(),
                    message.eventId(),
                    ChatBridgeMessages.INBOUND,
                    payload,
                    ACK_TIMEOUT
            );
        } catch (RuntimeException ignored) {
            // Ephemeral Discord chat is dropped on backend delivery failure.
        }
    }

    private Admission reserve(ChatBridgeInboundMessage message, long now) {
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

    private static Optional<ChatBridgeInboundMessage> decode(String payloadJson) {
        try {
            return Optional.of(ChatBridgeMessages.decodeInbound(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        binding.set(null);
        worker.shutdownNow();
        synchronized (dedupeLock) {
            dedupeUntil.clear();
        }
    }
}

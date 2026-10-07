package net.enthusia.staff.discordbot;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/**
 * Bounded StaffBot admission queue for authenticated Velocity chat frames.
 *
 * <p>ACK eligibility is decided by {@link #accept(ProtocolEnvelope)}. Discord delivery happens
 * later on one dedicated worker and is never retried.</p>
 */
final class StaffBotChatIngress implements AutoCloseable {
    private record Admission(boolean accepted, boolean duplicate, long expiresAt) {
    }

    private final Clock clock;
    private final Map<StaffBotChatBridgeConfiguration.Route, Long> routes;
    private final DiscordChatEgress egress;
    private final int maximumDedupeEntries;
    private final Map<UUID, Long> dedupeUntil = new ConcurrentHashMap<>();
    private final Object dedupeLock = new Object();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final ThreadPoolExecutor worker;

    StaffBotChatIngress(
            StaffBotChatBridgeConfiguration configuration,
            Clock clock,
            DiscordChatEgress egress
    ) {
        this(
                configuration.routes(),
                configuration.queueCapacity(),
                configuration.dedupeCapacity(),
                clock,
                egress
        );
    }

    StaffBotChatIngress(
            Map<StaffBotChatBridgeConfiguration.Route, Long> routes,
            int queueCapacity,
            int maximumDedupeEntries,
            Clock clock,
            DiscordChatEgress egress
    ) {
        this.routes = Map.copyOf(Objects.requireNonNull(routes, "routes"));
        if (this.routes.isEmpty() || queueCapacity < 1 || maximumDedupeEntries < 1) {
            throw new IllegalArgumentException("StaffBot chat ingress bounds/routes are invalid");
        }
        this.maximumDedupeEntries = maximumDedupeEntries;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.egress = Objects.requireNonNull(egress, "egress");
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-StaffBot-Chat");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!accepting.get()
                || !StaffBotChatBridgeConfiguration.PROXY_ID.equals(envelope.serverId())
                || !ChatBridgeMessages.OUTBOUND.equals(envelope.messageType())) {
            return false;
        }

        Optional<ChatBridgeOutboundMessage> decoded = decode(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeOutboundMessage message = decoded.orElseThrow();
        long now = clock.millis();
        if (!message.eventId().equals(envelope.messageId()) || message.isExpired(now)) {
            return false;
        }

        Long channelId = routes.get(new StaffBotChatBridgeConfiguration.Route(
                message.sourceServerId(), message.logicalChannelId()));
        if (channelId == null) {
            return false;
        }

        long expectedGeneration = generation.get();
        if (!accepting.get()) {
            return false;
        }
        Admission admission = reserve(message, now);
        if (!admission.accepted()) {
            return admission.duplicate();
        }

        try {
            worker.execute(() -> deliver(expectedGeneration, channelId, message));
            return true;
        } catch (RejectedExecutionException failure) {
            release(message.eventId(), admission.expiresAt());
            return false;
        }
    }

    void resume() {
        generation.incrementAndGet();
        accepting.set(true);
    }

    void pause() {
        accepting.set(false);
        generation.incrementAndGet();
        worker.getQueue().clear();
        synchronized (dedupeLock) {
            dedupeUntil.clear();
        }
    }

    private static Optional<ChatBridgeOutboundMessage> decode(String payloadJson) {
        try {
            return Optional.of(ChatBridgeMessages.decodeOutbound(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private Admission reserve(ChatBridgeOutboundMessage message, long now) {
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

    private void deliver(
            long expectedGeneration,
            long channelId,
            ChatBridgeOutboundMessage message
    ) {
        if (!accepting.get()
                || generation.get() != expectedGeneration
                || message.isExpired(clock.millis())) {
            return;
        }
        try {
            egress.send(channelId, message);
        } catch (RuntimeException ignored) {
            // Discord chat is best-effort and never escalates into StaffBot lifecycle failure.
        }
    }

    @Override
    public void close() {
        pause();
        worker.shutdownNow();
    }
}

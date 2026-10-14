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
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/** Bounded StaffBot admission queue for authenticated styled chat frames. */
final class StaffBotRenderedChatIngress implements AutoCloseable {
    private record Admission(boolean accepted, boolean duplicate, long expiresAt) {
    }

    private final Clock clock;
    private final Map<StaffBotChatBridgeConfiguration.Route, Long> routes;
    private final DiscordRenderedChatEgress egress;
    private final StaffBotChatArtifactStore artifactStore;
    private final int maximumDedupeEntries;
    private final Map<UUID, Long> dedupeUntil = new ConcurrentHashMap<>();
    private final Object dedupeLock = new Object();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final ThreadPoolExecutor worker;

    StaffBotRenderedChatIngress(
            StaffBotChatBridgeConfiguration configuration,
            Clock clock,
            DiscordRenderedChatEgress egress,
            StaffBotChatArtifactStore artifactStore
    ) {
        this(
                configuration.routes(),
                configuration.queueCapacity(),
                configuration.dedupeCapacity(),
                clock,
                egress,
                artifactStore
        );
    }

    StaffBotRenderedChatIngress(
            Map<StaffBotChatBridgeConfiguration.Route, Long> routes,
            int queueCapacity,
            int maximumDedupeEntries,
            Clock clock,
            DiscordRenderedChatEgress egress
    ) {
        this(
                routes,
                queueCapacity,
                maximumDedupeEntries,
                clock,
                egress,
                new StaffBotChatArtifactStore()
        );
    }

    StaffBotRenderedChatIngress(
            Map<StaffBotChatBridgeConfiguration.Route, Long> routes,
            int queueCapacity,
            int maximumDedupeEntries,
            Clock clock,
            DiscordRenderedChatEgress egress,
            StaffBotChatArtifactStore artifactStore
    ) {
        this.routes = Map.copyOf(Objects.requireNonNull(routes, "routes"));
        if (this.routes.isEmpty() || queueCapacity < 1 || maximumDedupeEntries < 1) {
            throw new IllegalArgumentException("StaffBot styled chat ingress bounds/routes are invalid");
        }
        this.maximumDedupeEntries = maximumDedupeEntries;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.egress = Objects.requireNonNull(egress, "egress");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-StaffBot-Styled-Chat");
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
                || !ChatRenderMessages.RENDERED.equals(envelope.messageType())) {
            return false;
        }
        Optional<ChatBridgeRenderedMessage> decoded = decode(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeRenderedMessage message = decoded.orElseThrow();
        long now = clock.millis();
        if (!message.eventId().equals(envelope.messageId()) || message.isExpired(now)) {
            return false;
        }
        Long channelId = routeChannel(message);
        return channelId != null && admit(channelId, message, now);
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
        artifactStore.clear();
    }

    private static Optional<ChatBridgeRenderedMessage> decode(String payloadJson) {
        try {
            return Optional.of(ChatRenderMessages.decode(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private Long routeChannel(ChatBridgeRenderedMessage message) {
        try {
            StaffBotChatBridgeConfiguration.Route route =
                    new StaffBotChatBridgeConfiguration.Route(
                            message.sourceServerId(),
                            message.logicalChannelId()
                    );
            return routes.get(route);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    private boolean admit(long channelId, ChatBridgeRenderedMessage message, long now) {
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

    private void deliver(
            long expectedGeneration,
            long channelId,
            ChatBridgeRenderedMessage message
    ) {
        if (!accepting.get()
                || generation.get() != expectedGeneration
                || message.isExpired(clock.millis())) {
            return;
        }
        try {
            egress.sendRendered(
                    channelId,
                    message,
                    artifactStore.consume(
                            message.eventId(),
                            message.sourceServerId(),
                            message.logicalChannelId(),
                            clock.millis()
                    )
            );
        } catch (RuntimeException ignored) {
            // Styled Discord chat is ephemeral and never escalates into lifecycle failure.
        }
    }

    @Override
    public void close() {
        pause();
        worker.shutdownNow();
    }
}

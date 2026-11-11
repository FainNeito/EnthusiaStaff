package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.protocol.ChatBridgeHealthMessage;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.PersistentChannelClient;

/** Publishes short-lived Discord/JDA chat readiness over the authenticated StaffBot channel. */
final class StaffBotChatReadinessPublisher implements AutoCloseable {
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    static final Duration READINESS_LIFETIME = Duration.ofSeconds(15);
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    private final Clock clock;
    private final PersistentChannelClient client;
    private final boolean authorityEnabled;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "EnthusiaStaff-StaffBot-Chat-Readiness");
        thread.setDaemon(true);
        return thread;
    });

    StaffBotChatReadinessPublisher(Clock clock, PersistentChannelClient client, boolean authorityEnabled) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.client = Objects.requireNonNull(client, "client");
        this.authorityEnabled = authorityEnabled;
    }

    void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleAtFixedRate(
                this::publishCurrent,
                0L,
                HEARTBEAT_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    void resume() {
        if (closed.get()) {
            return;
        }
        ready.set(authorityEnabled);
        publishCurrent();
    }

    void pause() {
        ready.set(false);
        publishCurrent();
    }

    private void publishCurrent() {
        if (closed.get() || !client.connected()) {
            return;
        }
        long now = clock.millis();
        ChatBridgeHealthMessage message = new ChatBridgeHealthMessage(
                ready.get(),
                now,
                now + READINESS_LIFETIME.toMillis()
        );
        try {
            client.send(
                    UUID.randomUUID(),
                    ChatBridgeMessages.HEALTH,
                    ChatBridgeMessages.encodeHealth(message),
                    ACK_TIMEOUT
            );
        } catch (RuntimeException ignored) {
            // Readiness is advisory and short-lived; Paper fails safe when heartbeats expire.
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ready.set(false);
        if (client.connected()) {
            long now = clock.millis();
            try {
                client.send(
                        UUID.randomUUID(),
                        ChatBridgeMessages.HEALTH,
                        ChatBridgeMessages.encodeHealth(new ChatBridgeHealthMessage(false, now, now + 1_000L)),
                        ACK_TIMEOUT
                );
            } catch (RuntimeException ignored) {
                // Expiry is the fallback when an explicit unhealthy frame cannot be sent.
            }
        }
        scheduler.shutdownNow();
    }
}

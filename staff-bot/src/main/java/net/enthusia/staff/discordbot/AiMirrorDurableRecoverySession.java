package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Staging-only lifecycle composition; NOT installed or loaded by StaffBotRuntime.
 * A future reviewed runtime gate must choose this OR volatile registration.
 * Never subscribe both to the one actual JDA post-send observer.
 */
final class AiMirrorDurableRecoverySession
        implements DiscordChatPostObserver, StaffBotChatLifecycle {
    private static final Duration REQUEST_DEADLINE = Duration.ofSeconds(2);
    private static final String ERROR_HEADER = "X-Moderation-Mirror-Error";

    private final StaffBotChatLifecycle chat;
    private final AiMirrorEncryptedOutbox store;
    private final AiMirrorDurableReceiptIngress ingress;
    private final AiMirrorRecoveryScheduler recovery;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean recoveryStarted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    static AiMirrorDurableRecoverySession open(
            AiMirrorDurableStagingSettings settings,
            AiMirrorPublicationConfig transport,
            StaffBotChatLifecycle chat,
            Clock clock) throws IOException {
        Objects.requireNonNull(settings, "settings");
        AiMirrorEncryptedOutbox store = settings.openOutbox(clock);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(REQUEST_DEADLINE)
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            return new AiMirrorDurableRecoverySession(chat, store, transport, request -> {
                HttpResponse<Void> response = client.send(
                        request, HttpResponse.BodyHandlers.discarding());
                return new AiMirrorRegistrationPublisher.PostResult(
                        response.statusCode(),
                        response.headers().firstValue(ERROR_HEADER).orElse(""));
            });
        } catch (RuntimeException failure) {
            store.close();
            throw failure;
        }
    }

    AiMirrorDurableRecoverySession(
            StaffBotChatLifecycle chat, AiMirrorEncryptedOutbox store,
            AiMirrorPublicationConfig config,
            AiMirrorRegistrationPublisher.DetailedPoster poster) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.store = Objects.requireNonNull(store, "store");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(poster, "poster");
        this.ingress = AiMirrorDurableReceiptIngress.withStore(config, store);
        this.recovery = new AiMirrorRecoveryScheduler(
                store, new AiMirrorReceiptReplay(store, config, poster));
    }

    @Override
    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            throw new IllegalStateException("durable staging chat already started or closed");
        }
        chat.start();
        // No recovery HTTP before JDA's staging identity readiness callback.
    }

    @Override
    public void resume() {
        ensureReadyForResume();
        try {
            chat.resume();
            ingress.resume();
            resumeRecovery();
        } catch (RuntimeException failure) {
            ingress.pause();
            recovery.pause();
            throw failure;
        }
    }

    private void ensureReadyForResume() {
        if (closed.get() || !started.get()) {
            throw new IllegalStateException("durable staging chat not started");
        }
        if (ingress.failed() || recovery.failed()) {
            throw new IllegalStateException("durable staging mirror storage unavailable");
        }
    }

    private void resumeRecovery() {
        boolean scheduled = recoveryStarted.compareAndSet(false, true)
                ? recovery.start() : recovery.resume();
        if (!scheduled && (ingress.failed() || recovery.failed())) {
            throw new IllegalStateException("durable staging mirror recovery unavailable");
        }
    }

    @Override
    public void pause() {
        ingress.pause();
        recovery.pause();
        chat.pause();
    }

    @Override
    public void published(DiscordChatPublication publication) {
        if (!closed.get()) {
            ingress.published(publication);
        }
    }

    AiMirrorDurableReceiptIngress.Stats ingressStats() {
        return ingress.stats();
    }

    AiMirrorRecoveryScheduler.Stats recoveryStats() {
        return recovery.stats();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ingress.close();
        recovery.close();
        try {
            store.close();
        } catch (IOException failure) {
            // No exception detail: file paths or storage metadata must not leak.
            throw new IllegalStateException("encrypted mirror journal could not close");
        } finally {
            chat.close();
        }
    }
}

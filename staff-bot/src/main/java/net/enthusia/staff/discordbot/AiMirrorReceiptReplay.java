package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.Optional;

/**
 * Inert, one-receipt slow recovery step. The caller must be an off-JDA
 * background worker with its own global rate limit. No scheduler or runtime
 * hookup is created by this class.
 *
 * Each HTTP attempt consumes an encrypted, fsynced, restart-durable budget
 * token first. Terminal errors never retry or guess canonical identities.
 */
final class AiMirrorReceiptReplay {
    private static final int HTTP_OK = 200;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_UNAVAILABLE = 503;

    enum Outcome { CONFIRMED, TRANSIENT, TERMINAL, EXHAUSTED }

    private final AiMirrorEncryptedOutbox store;
    private final AiMirrorPublicationConfig config;
    private final AiMirrorRegistrationPublisher.DetailedPoster poster;

    AiMirrorReceiptReplay(
            AiMirrorEncryptedOutbox store,
            AiMirrorPublicationConfig config,
            AiMirrorRegistrationPublisher.DetailedPoster poster) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.poster = Objects.requireNonNull(poster, "poster");
        if (!store.accepts(config)) {
            throw new IllegalArgumentException("recovery configuration scope mismatch");
        }
    }

    Outcome attemptOne(AiMirrorEncryptedOutbox.Saved receipt)
            throws IOException, InterruptedException {
        // This is the only method that can issue HTTP; it never runs on JDA.
        Optional<AiMirrorEncryptedOutbox.Saved> prepared =
                store.prepareRecoveryAttempt(receipt);
        if (prepared.isEmpty()) {
            return Outcome.EXHAUSTED;
        }
        AiMirrorEncryptedOutbox.Saved counted = prepared.orElseThrow();
        final HttpRequest request;
        try {
            request = AiMirrorRegistrationPublisher.request(config, counted.publication());
        } catch (IOException | RuntimeException failure) {
            // No HTTP request was sent; never expose original chat text in an
            // exception from JSON body creation or request validation.
            throw new IOException("untrusted recovery request");
        }
        final AiMirrorRegistrationPublisher.PostResult response;
        try {
            response = poster.post(request);
        } catch (HttpTimeoutException timeout) {
            return Outcome.TRANSIENT;
        } catch (IOException offline) {
            return Outcome.TRANSIENT;
        }
        if (response.status() == HTTP_OK) {
            store.acknowledge(counted);
            return Outcome.CONFIRMED;
        }
        if (retryable(response)) {
            return Outcome.TRANSIENT;
        }
        // Never repeat 404, identity collision, bad auth, bad schema, redirects
        // or an unknown 409/503 after a restart.
        store.acknowledge(counted);
        return Outcome.TERMINAL;
    }

    private static boolean retryable(AiMirrorRegistrationPublisher.PostResult response) {
        if (response.status() == HTTP_CONFLICT) {
            return "canonical_pending".equals(response.errorCode());
        }
        return response.status() == HTTP_UNAVAILABLE
                && ("timeout".equals(response.errorCode())
                || "storage_unavailable".equals(response.errorCode()));
    }
}

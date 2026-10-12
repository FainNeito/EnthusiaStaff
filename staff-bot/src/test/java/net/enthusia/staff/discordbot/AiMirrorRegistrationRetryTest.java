package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Local synthetic response tests; no Discord, credentials, production service or network. */
class AiMirrorRegistrationRetryTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final long WAIT_SECONDS = 6L;
    private static final long CANCEL_SETTLE_MILLIS = 400L;
    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_UNAVAILABLE = 503;
    private static final int MAX_ATTEMPTS = 4;

    @Test
    void pendingThenFinalRetriesOnlyRegistrationAndConfirmsOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch confirmed = new CountDownLatch(1);
        try (AiMirrorRegistrationPublisher publisher = AiMirrorRegistrationPublisher.withDetailedPoster(
                config(), request -> {
                    if (calls.incrementAndGet() == 1) {
                        return new AiMirrorRegistrationPublisher.PostResult(
                                HTTP_CONFLICT, "canonical_pending");
                    }
                    confirmed.countDown();
                    return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(confirmed.await(WAIT_SECONDS, TimeUnit.SECONDS));
            Thread.sleep(CANCEL_SETTLE_MILLIS);
            assertEquals(2, calls.get());
            assertEquals(1L, publisher.stats().confirmed());
            assertEquals(1L, publisher.stats().pendingResponses());
            assertEquals(1L, publisher.stats().retriesScheduled());
            assertEquals(0L, publisher.stats().conflicts());
        }
    }

    @Test
    void identityCollisionNeverRetriesEvenWithConflicting409() throws Exception {
        assertTerminal(HTTP_CONFLICT, "mirror_conflict");
    }

    @Test
    void unknownCanonicalAndOtherUntrustedFailuresNeverRetry() throws Exception {
        assertTerminal(HTTP_NOT_FOUND, "canonical_missing");
        assertTerminal(HTTP_CONFLICT, "");
        assertTerminal(HTTP_UNAVAILABLE, "");
        assertTerminal(401, "");
        assertTerminal(403, "");
        assertTerminal(422, "");
        assertTerminal(302, "");
    }

    private static void assertTerminal(int status, String code) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch attempted = new CountDownLatch(1);
        try (AiMirrorRegistrationPublisher publisher = AiMirrorRegistrationPublisher.withDetailedPoster(
                config(), request -> {
                    calls.incrementAndGet();
                    attempted.countDown();
                    return new AiMirrorRegistrationPublisher.PostResult(status, code);
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(attempted.await(WAIT_SECONDS, TimeUnit.SECONDS));
            Thread.sleep(CANCEL_SETTLE_MILLIS);
            assertEquals(1, calls.get(), "Unexpected retry for HTTP " + status + " " + code);
            assertEquals(0L, publisher.stats().retriesScheduled());
            assertEquals(status == HTTP_CONFLICT && "mirror_conflict".equals(code) ? 1L : 0L,
                    publisher.stats().conflicts());
        }
    }

    @Test
    void temporaryStorageErrorCanRecoverWithinBound() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch confirmed = new CountDownLatch(1);
        try (AiMirrorRegistrationPublisher publisher = AiMirrorRegistrationPublisher.withDetailedPoster(
                config(), request -> {
                    if (calls.incrementAndGet() <= 2) {
                        return new AiMirrorRegistrationPublisher.PostResult(
                                HTTP_UNAVAILABLE, "storage_unavailable");
                    }
                    confirmed.countDown();
                    return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(confirmed.await(WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(3, calls.get());
            assertEquals(1L, publisher.stats().confirmed());
            assertEquals(2L, publisher.stats().unavailable());
        }
    }

    @Test
    void repeatedHttpTimeoutCannotRetryIndefinitely() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch exhausted = new CountDownLatch(MAX_ATTEMPTS);
        try (AiMirrorRegistrationPublisher publisher = AiMirrorRegistrationPublisher.withDetailedPoster(
                config(), request -> {
                    calls.incrementAndGet();
                    exhausted.countDown();
                    throw new HttpTimeoutException("synthetic timeout, no network");
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(exhausted.await(WAIT_SECONDS, TimeUnit.SECONDS));
            Thread.sleep(CANCEL_SETTLE_MILLIS);
            assertEquals(MAX_ATTEMPTS, calls.get());
            assertEquals(MAX_ATTEMPTS, publisher.stats().timeoutFailures());
            assertEquals(1L, publisher.stats().expired());
            assertEquals(MAX_ATTEMPTS - 1, publisher.stats().retriesScheduled());
        }
    }

    @Test
    void ordinaryIoFailureAlsoHasStrictAttemptBound() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch exhausted = new CountDownLatch(MAX_ATTEMPTS);
        try (AiMirrorRegistrationPublisher publisher = AiMirrorRegistrationPublisher.withDetailedPoster(
                config(), request -> {
                    calls.incrementAndGet();
                    exhausted.countDown();
                    throw new IOException("synthetic connection unavailable");
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(exhausted.await(WAIT_SECONDS, TimeUnit.SECONDS));
            Thread.sleep(CANCEL_SETTLE_MILLIS);
            assertEquals(MAX_ATTEMPTS, calls.get());
            assertEquals(1L, publisher.stats().expired());
            assertEquals(MAX_ATTEMPTS, publisher.stats().unavailable());
        }
    }

    @Test
    void pauseAndResumeCancelOlderDelayedRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch fresh = new CountDownLatch(1);
        try (AiMirrorRegistrationPublisher publisher = AiMirrorRegistrationPublisher.withDetailedPoster(
                config(), request -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        first.countDown();
                        return new AiMirrorRegistrationPublisher.PostResult(
                                HTTP_CONFLICT, "canonical_pending");
                    }
                    fresh.countDown();
                    return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                })) {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(first.await(WAIT_SECONDS, TimeUnit.SECONDS));
            publisher.pause();
            publisher.resume();
            Thread.sleep(CANCEL_SETTLE_MILLIS);
            assertEquals(1, calls.get(), "Prior generation retried after pause");

            publisher.published(receipt());
            assertTrue(fresh.await(WAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void closeDropsDelayedRetriesRatherThanRetainingMessagePayloads() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        AiMirrorRegistrationPublisher publisher = AiMirrorRegistrationPublisher.withDetailedPoster(
                config(), request -> {
                    calls.incrementAndGet();
                    first.countDown();
                    return new AiMirrorRegistrationPublisher.PostResult(
                            HTTP_CONFLICT, "canonical_pending");
                });
        try {
            publisher.resume();
            publisher.published(receipt());
            assertTrue(first.await(WAIT_SECONDS, TimeUnit.SECONDS));
            publisher.close();
            publisher.resume();
            publisher.published(receipt());
            Thread.sleep(CANCEL_SETTLE_MILLIS);
            assertEquals(1, calls.get());
        } finally {
            publisher.close();
        }
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD, Set.of(CHANNEL),
                "discord_general");
    }

    private static DiscordChatPublication receipt() {
        return new DiscordChatPublication(
                GUILD, CHANNEL, "1234567890123456787",
                "rosechat:synthetic:retry", "synthetic original public chat");
    }
}

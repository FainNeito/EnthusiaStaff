package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** No JDA, no live API; HTTP poster is always a synthetic callback. */
class AiMirrorImmediateRegistrationTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final int HTTP_OK = 200;
    private static final int FIRST_ATTEMPT = 1;
    private static final int HTTP_PENDING = 409;
    private static final int HTTP_MISSING = 404;
    private static final int HTTP_UNAVAILABLE = 503;
    private static final int RECEIPTS = 12;
    private static final int SATURATED = 70;
    private static final int MAX_PENDING = 64;
    private static final int SECONDS = 5;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);

    @TempDir Path directory;

    @BeforeEach
    void privateDirectory() throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void healthyReceiptsRegisterImmediatelyWithoutSlowOnePerMinuteThrottle()
            throws Exception {
        try (AiMirrorEncryptedOutbox store = outbox();
                AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                        store, config(), request ->
                        new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, ""))) {
            immediate.resume();
            for (int index = 0; index < RECEIPTS; index++) {
                assertTrue(immediate.offer(store.save(receipt(index))));
            }
            awaitConfirmed(immediate, RECEIPTS);
            assertTrue(store.recover().isEmpty());
            assertEquals(0, immediate.stats().deferredToRecovery());
        }
    }

    @Test
    void pendingAndUnavailableSourceRemainEncryptedForSlowRecovery()
            throws Exception {
        AtomicInteger sent = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = outbox();
                AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                        store, config(), request -> sent.incrementAndGet() == FIRST_ATTEMPT
                        ? new AiMirrorRegistrationPublisher.PostResult(
                                HTTP_PENDING, "canonical_pending")
                        : new AiMirrorRegistrationPublisher.PostResult(
                                HTTP_UNAVAILABLE, "storage_unavailable"))) {
            immediate.resume();
            assertTrue(immediate.offer(store.save(receipt(1))));
            assertTrue(immediate.offer(store.save(receipt(2))));
            awaitTransient(immediate, 2);
            assertEquals(2, store.recover().size());
            assertEquals(0, immediate.stats().confirmed());
        }
    }

    @Test
    void sourceMissingIsTerminalWhileNetworkFailureRetainsRecord()
            throws Exception {
        AtomicInteger called = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = outbox();
                AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                        store, config(), request -> {
                            if (called.incrementAndGet() == FIRST_ATTEMPT) {
                                return new AiMirrorRegistrationPublisher.PostResult(
                                        HTTP_MISSING, "canonical_missing");
                            }
                            throw new IOException("synthetic transport offline");
                        })) {
            immediate.resume();
            assertTrue(immediate.offer(store.save(receipt(1))));
            assertTrue(immediate.offer(store.save(receipt(2))));
            awaitTransient(immediate, 1);
            assertEquals(1, immediate.stats().terminalDiscarded());
            assertEquals(1, store.recover().size());
        }
    }

    @Test
    void saturatedHttpWorkerDoesNotLosePersistedReceipts() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (AiMirrorEncryptedOutbox store = outbox();
                AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                        store, config(), request -> {
                            entered.countDown();
                            if (!release.await(SECONDS, TimeUnit.SECONDS)) {
                                throw new IOException("synthetic request stalled");
                            }
                            return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                        })) {
            immediate.resume();
            assertTrue(immediate.offer(store.save(receipt(0))));
            assertTrue(entered.await(SECONDS, TimeUnit.SECONDS));
            long started = System.nanoTime();
            for (int index = 1; index <= SATURATED; index++) {
                immediate.offer(store.save(receipt(index)));
            }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                    < TimeUnit.SECONDS.toMillis(SECONDS));
            assertEquals(SATURATED - MAX_PENDING,
                    immediate.stats().deferredToRecovery());
            immediate.pause();
            assertEquals(SATURATED, immediate.stats().deferredToRecovery());
            assertEquals(SATURATED + 1, store.recover().size());
            release.countDown();
        } finally {
            release.countDown();
        }
    }

    @Test
    void pausedOrClosedWorkerRefusesNewOffersWithoutTouchingJournal()
            throws Exception {
        try (AiMirrorEncryptedOutbox store = outbox();
                AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                        store, config(), request ->
                        new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, ""))) {
            var committed = store.save(receipt(1));
            assertFalse(immediate.offer(committed));
            immediate.resume();
            immediate.pause();
            assertFalse(immediate.offer(committed));
            assertEquals(2, immediate.stats().deferredToRecovery());
            assertEquals(1, store.recover().size());
        }
    }

    @Test
    void typedPermanentStatusesDiscardTheExactReceipts() throws Exception {
        AiMirrorRegistrationPublisher.PostResult[] results = {
            new AiMirrorRegistrationPublisher.PostResult(HTTP_PENDING, "mirror_conflict"),
            new AiMirrorRegistrationPublisher.PostResult(HTTP_UNAVAILABLE, ""),
            new AiMirrorRegistrationPublisher.PostResult(401, ""),
            new AiMirrorRegistrationPublisher.PostResult(302, "")
        };
        AtomicInteger sent = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = outbox();
                AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                        store, config(), request -> results[sent.getAndIncrement()])) {
            immediate.resume();
            for (int index = 0; index < results.length; index++) {
                assertTrue(immediate.offer(store.save(receipt(index))));
            }
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
            while (immediate.stats().terminalDiscarded() != results.length
                    && System.nanoTime() < end) {
                Thread.sleep(10);
            }
            assertEquals(results.length, immediate.stats().terminalDiscarded());
            assertTrue(store.recover().isEmpty());
        }
    }

    @Test
    void staleAcknowledgementStopsAdmissionAndPreservesOriginal() throws Exception {
        try (AiMirrorEncryptedOutbox store = outbox();
                AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                        store, config(), request ->
                        new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, ""))) {
            AiMirrorEncryptedOutbox.Saved saved = store.save(receipt(1));
            AiMirrorEncryptedOutbox.Saved stale = new AiMirrorEncryptedOutbox.Saved(
                    saved.fileName(), saved.publication(), saved.createdMillis(),
                    saved.recoveryAttempts() + 1, saved.lastAttemptMillis());
            immediate.resume();
            assertTrue(immediate.offer(stale));
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
            while (!immediate.failed() && System.nanoTime() < end) {
                Thread.sleep(10);
            }
            assertTrue(immediate.failed());
            assertFalse(immediate.offer(store.save(receipt(2))));
            assertEquals(1, immediate.stats().storageFailures());
            assertEquals(2, store.recover().size());
        }
    }

    @Test
    void pauseDrainsQueuedReceiptsEvenWhenResumed() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = outbox()) {
            AiMirrorImmediateRegistration immediate = new AiMirrorImmediateRegistration(
                    store, config(), request -> {
                        calls.incrementAndGet();
                        entered.countDown();
                        release.await(SECONDS, TimeUnit.SECONDS);
                        return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                    });
            try {
                immediate.resume();
                assertTrue(immediate.offer(store.save(receipt(1))));
                assertTrue(entered.await(SECONDS, TimeUnit.SECONDS));
                assertTrue(immediate.offer(store.save(receipt(2))));
                immediate.pause();
                assertEquals(1, immediate.stats().deferredToRecovery());
                immediate.resume();
                release.countDown();
                awaitConfirmed(immediate, 1);
            } finally {
                release.countDown();
                immediate.close();
            }
            assertTrue(immediate.awaitStopped(java.time.Duration.ofSeconds(SECONDS)));
            assertEquals(1, calls.get());
            assertEquals(1, store.recover().size());
        }
    }

    private static void awaitConfirmed(AiMirrorImmediateRegistration immediate, int expected)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
        while (immediate.stats().confirmed() != expected && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertEquals(expected, immediate.stats().confirmed());
    }

    private static void awaitTransient(AiMirrorImmediateRegistration immediate, int expected)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
        while (immediate.stats().transientRetained() != expected
                && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertEquals(expected, immediate.stats().transientRetained());
    }

    private AiMirrorEncryptedOutbox outbox() throws IOException {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x48);
        return new AiMirrorEncryptedOutbox(directory, key,
                "staging:synthetic-bridge:example.invalid:443",
                GUILD, Set.of(CHANNEL), "discord_general", CLOCK);
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication receipt(int index) {
        return new DiscordChatPublication(GUILD, CHANNEL,
                "1234567890123456787", "rosechat:synthetic:" + index,
                "synthetic undecorated public chat " + index);
    }
}

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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Only pre-encrypted synthetic receipts; no JDA or external network. */
class AiMirrorRecoverySchedulerTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
    private static final String DESTINATION = "staging:synthetic-bridge:example.invalid:443";
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAVAILABLE = 503;
    private static final int AWAIT_SECONDS = 5;

    @TempDir Path directory;

    @BeforeEach
    void privateDirectory() throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void oneBackgroundAttemptAcknowledgesExactlyTheRecoveredReceipt()
            throws Exception {
        try (AiMirrorEncryptedOutbox store = open()) {
            store.save(receipt());
            CountDownLatch called = new CountDownLatch(1);
            AtomicReference<String> workerThread = new AtomicReference<>();
            var replay = new AiMirrorReceiptReplay(store, config(), request -> {
                workerThread.set(Thread.currentThread().getName());
                called.countDown();
                return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
            });
            try (AiMirrorRecoveryScheduler scheduler = new AiMirrorRecoveryScheduler(store, replay)) {
                assertTrue(scheduler.start());
                assertFalse(scheduler.start());
                assertTrue(called.await(AWAIT_SECONDS, TimeUnit.SECONDS));
                awaitEmpty(store);
                assertTrue(workerThread.get().contains("AI-Mirror-Recovery-Prototype"));
                awaitConfirmed(scheduler);
                assertFalse(scheduler.failed());
            }
        }
    }

    @Test
    void transientFailureLeavesDurableCountForSubsequentProcessRestart()
            throws Exception {
        try (AiMirrorEncryptedOutbox original = open()) {
            original.save(receipt());
            CountDownLatch attempted = new CountDownLatch(1);
            var replay = new AiMirrorReceiptReplay(original, config(), request -> {
                attempted.countDown();
                return new AiMirrorRegistrationPublisher.PostResult(
                        HTTP_UNAVAILABLE, "storage_unavailable");
            });
            try (AiMirrorRecoveryScheduler scheduler =
                    new AiMirrorRecoveryScheduler(original, replay)) {
                assertTrue(scheduler.start());
                assertTrue(attempted.await(AWAIT_SECONDS, TimeUnit.SECONDS));
            }
            assertEquals(1, original.recover().getFirst().recoveryAttempts());
        }
        try (AiMirrorEncryptedOutbox recovered = open(java.time.Clock.offset(
                FIXED, java.time.Duration.ofSeconds(61)))) {
            CountDownLatch attempted = new CountDownLatch(1);
            var replay = new AiMirrorReceiptReplay(recovered, config(), request -> {
                attempted.countDown();
                return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
            });
            try (AiMirrorRecoveryScheduler scheduler =
                    new AiMirrorRecoveryScheduler(recovered, replay)) {
                assertTrue(scheduler.start());
                assertTrue(attempted.await(AWAIT_SECONDS, TimeUnit.SECONDS));
                awaitEmpty(recovered);
            }
        }
    }

    @Test
    void corruptedReceiptStopsAllRecoveryAttemptsWithoutLoggingContents()
            throws Exception {
        String opaqueName;
        try (AiMirrorEncryptedOutbox store = open()) {
            opaqueName = store.save(receipt()).fileName();
        }
        Path file = directory.resolve(opaqueName);
        byte[] sealed = Files.readAllBytes(file);
        sealed[sealed.length - 1] ^= 1;
        Files.write(file, sealed);
        AtomicInteger requests = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = open()) {
            var replay = new AiMirrorReceiptReplay(store, config(), request -> {
                requests.incrementAndGet();
                return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
            });
            try (AiMirrorRecoveryScheduler scheduler =
                    new AiMirrorRecoveryScheduler(store, replay)) {
                assertTrue(scheduler.start());
                awaitFailure(scheduler);
                assertEquals(1, scheduler.stats().storageFailures());
                assertEquals(0, requests.get());
            }
        }
    }

    @Test
    void expiredOfflineReceiptIsReportedWithoutIssuingHttp() throws Exception {
        try (AiMirrorEncryptedOutbox first = open()) {
            first.save(receipt());
        }
        Clock afterTtl = Clock.offset(FIXED,
                java.time.Duration.ofHours(1).plusSeconds(1));
        try (AiMirrorEncryptedOutbox restored = open(afterTtl)) {
            var replay = new AiMirrorReceiptReplay(restored, config(), request -> {
                throw new AssertionError("expired receipt must never reach API");
            });
            try (AiMirrorRecoveryScheduler scheduler = new AiMirrorRecoveryScheduler(
                    restored, replay)) {
                assertTrue(scheduler.start());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
                while (scheduler.stats().expiredReceipts() == 0
                        && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertEquals(1L, scheduler.stats().expiredReceipts());
                assertTrue(restored.recover().isEmpty());
                assertEquals(1L, scheduler.stats().expiredReceipts());
                assertFalse(scheduler.failed());
            }
        }
    }

    private static void awaitConfirmed(AiMirrorRecoveryScheduler scheduler)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (scheduler.stats().confirmed() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, scheduler.stats().confirmed());
    }

    private static void awaitEmpty(AiMirrorEncryptedOutbox store) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (!store.recover().isEmpty() && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertTrue(store.recover().isEmpty());
    }

    private static void awaitFailure(AiMirrorRecoveryScheduler scheduler)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (!scheduler.failed() && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertTrue(scheduler.failed());
    }

    private AiMirrorEncryptedOutbox open() throws IOException {
        return open(FIXED);
    }

    private AiMirrorEncryptedOutbox open(Clock clock) throws IOException {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x48);
        return new AiMirrorEncryptedOutbox(
                directory, key, DESTINATION, GUILD, Set.of(CHANNEL),
                "discord_general", clock);
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication receipt() {
        return new DiscordChatPublication(
                GUILD, CHANNEL, "1234567890123456787",
                "rosechat:synthetic:scheduler", "synthetic public original text");
    }
}

package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Entirely disposable synthetic receipts; no JDA, real Discord, or live API. */
class AiMirrorFastSessionIntegrationTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final int HTTP_OK = 200;
    private static final int BURST = 20;
    private static final int WAIT_SECONDS = 15;
    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
    @TempDir Path directory;

    @BeforeEach
    void secureDirectory() throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void stagingSessionRegistersHealthyBurstWithoutMinuteSpacing() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = openStore();
                AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                        new FakeChat(), store, config(), request -> {
                            requests.incrementAndGet();
                            return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                        })) {
            session.start();
            session.resume();
            for (int i = 0; i < BURST; i++) {
                session.published(publication(i));
                // Realistic stagger avoids deliberately overflowing the separate
                // bounded disk ingress queue with a synthetic zero-delay burst.
                Thread.sleep(20);
            }
            awaitProcessed(session, BURST);
            assertEquals(BURST, requests.get());
            assertEquals(BURST, session.ingressStats().persisted());
            assertTrue(session.immediateStats().confirmed() >= BURST - 1);
            assertTrue(store.recover().isEmpty());
            assertFalse(session.unhealthy());
        }
    }

    @Test
    void fastClaimExcludesSlowHttpUntilTheFastAckCompletes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = openStore()) {
            AiMirrorReceiptClaims claims = new AiMirrorReceiptClaims();
            AiMirrorEncryptedOutbox.Saved saved = store.save(publication(1));
            AiMirrorRegistrationPublisher.DetailedPoster poster = request -> {
                sends.incrementAndGet();
                entered.countDown();
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IOException("synthetic HTTP wait expired");
                }
                return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
            };
            try (AiMirrorImmediateRegistration fast =
                    new AiMirrorImmediateRegistration(store, config(), poster, claims)) {
                fast.resume();
                assertTrue(fast.offer(saved));
                assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS));
                AiMirrorReceiptReplay slow =
                        new AiMirrorReceiptReplay(store, config(), poster, claims);
                assertEquals(AiMirrorReceiptReplay.Outcome.DEFERRED, slow.attemptOne(saved));
                assertEquals(1, sends.get());
                release.countDown();
                awaitFastAck(fast);
                assertEquals(1, sends.get());
                assertEquals(0, claims.activeCount());
                assertTrue(store.recover().isEmpty());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void fastIgnoresAnAlreadyRemovedJournalRevisionWithoutSecondHttp() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        try (AiMirrorEncryptedOutbox store = openStore();
                AiMirrorImmediateRegistration fast = new AiMirrorImmediateRegistration(
                        store, config(), request -> {
                            sends.incrementAndGet();
                            return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
                        })) {
            AiMirrorEncryptedOutbox.Saved old = store.save(publication(2));
            store.acknowledge(old);
            fast.resume();
            assertTrue(fast.offer(old));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (fast.stats().deferredToRecovery() < 1 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, fast.stats().deferredToRecovery());
            assertEquals(0, sends.get());
            assertFalse(fast.failed());
        }
    }

    private static void awaitFastAck(AiMirrorImmediateRegistration fast)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (fast.stats().confirmed() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, fast.stats().confirmed());
    }

    private static void awaitProcessed(AiMirrorDurableRecoverySession session, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (session.immediateStats().confirmed()
                + session.recoveryStats().confirmed() < expected
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, session.immediateStats().confirmed()
                + session.recoveryStats().confirmed());
    }

    private AiMirrorEncryptedOutbox openStore() throws IOException {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x48);
        return new AiMirrorEncryptedOutbox(directory, key,
                "staging:synthetic-bridge:example.invalid:443", GUILD,
                Set.of(CHANNEL), "discord_general", FIXED);
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication publication(int i) {
        return new DiscordChatPublication(GUILD, CHANNEL,
                Long.toString(1234567890123456787L - i),
                "rosechat:synthetic:fast-session:" + i, "synthetic original " + i);
    }

    private static final class FakeChat implements StaffBotChatLifecycle {
        @Override public void start() { }
        @Override public void resume() { }
        @Override public void pause() { }
        @Override public void close() { }
    }
}

package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic one-shot recovery tests; no live Discord or external HTTP. */
class AiMirrorReceiptReplayTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String DESTINATION = "staging:synthetic-bridge:example.invalid:443";
    private static final String CANONICAL_TEXT = "synthetic exact original chat";
    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
    private static final int MAX_RECOVERY_ATTEMPTS = 8;
    private static final int HTTP_OK = 200;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_UNAVAILABLE = 503;

    @TempDir Path temporary;
    private Instant simulatedTime = FIXED.instant();

    @BeforeEach
    void permissions() throws IOException {
        if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(temporary,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void pendingAfterCrashIsReplayedWithExactlyTheOriginalScopedHttpBody()
            throws Exception {
        byte[] key = key();
        try (AiMirrorEncryptedOutbox first = open(key)) {
            first.save(receipt());
        }
        AtomicInteger called = new AtomicInteger();
        try (AiMirrorEncryptedOutbox resumed = open(key)) {
            var recovered = resumed.recover().getFirst();
            AiMirrorReceiptReplay replay = new AiMirrorReceiptReplay(resumed, config(), request -> {
                assertEquals(URI.create("http://127.0.0.1:23819/v1/mirror-aliases"), request.uri());
                String body = request.bodyPublisher().orElseThrow()
                        .contentLength() > 0 ? httpBody(request) : "";
                var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                assertEquals(CANONICAL_TEXT, json.get("canonical_text").asText());
                assertEquals("1234567890123456787", json.get("external_message_id").asText());
                return called.incrementAndGet() == 1
                        ? new AiMirrorRegistrationPublisher.PostResult(
                                HTTP_CONFLICT, "canonical_pending")
                        : new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.TRANSIENT, replay.attemptOne(recovered));
        }
        advanceTime();
        try (AiMirrorEncryptedOutbox resumed = open(key)) {
            var recovered = resumed.recover().getFirst();
            assertEquals(1, recovered.recoveryAttempts());
            AiMirrorReceiptReplay replay = new AiMirrorReceiptReplay(resumed, config(), request -> {
                called.incrementAndGet();
                return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.CONFIRMED, replay.attemptOne(recovered));
            assertTrue(resumed.recover().isEmpty());
            assertEquals(2, called.get());
        }
    }

    @Test
    void typedTransientStorageFailureCannotExceedBudgetAfterManyRestarts()
            throws Exception {
        byte[] key = key();
        try (AiMirrorEncryptedOutbox store = open(key)) {
            store.save(receipt());
        }
        AtomicInteger sent = new AtomicInteger();
        for (int index = 0; index < MAX_RECOVERY_ATTEMPTS; index++) {
            verifySingleTransientRestart(key, index, sent);
        }
        try (AiMirrorEncryptedOutbox restarted = open(key)) {
            var exhausted = restarted.recover().getFirst();
            assertEquals(MAX_RECOVERY_ATTEMPTS, exhausted.recoveryAttempts());
            var replay = new AiMirrorReceiptReplay(restarted, config(), request -> {
                sent.incrementAndGet();
                return new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, "");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.EXHAUSTED,
                    replay.attemptOne(exhausted));
            assertEquals(MAX_RECOVERY_ATTEMPTS, sent.get());
            assertTrue(restarted.recover().isEmpty());
        }
    }

    private void verifySingleTransientRestart(byte[] key, int expectedAttempts,
            AtomicInteger sent) throws Exception {
        if (expectedAttempts > 0) {
            advanceTime();
        }
        try (AiMirrorEncryptedOutbox restarted = open(key)) {
            var current = restarted.recover().getFirst();
            assertEquals(expectedAttempts, current.recoveryAttempts());
            var replay = new AiMirrorReceiptReplay(restarted, config(), request -> {
                sent.incrementAndGet();
                return new AiMirrorRegistrationPublisher.PostResult(
                        HTTP_UNAVAILABLE, "storage_unavailable");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.TRANSIENT,
                    replay.attemptOne(current));
        }
    }

    @Test
    void terminalIdentityAuthAndUnknownStatusesAreDurablyForgotten() throws Exception {
        int[] codes = {404, HTTP_CONFLICT, HTTP_CONFLICT, HTTP_UNAVAILABLE, 401, 422, 302};
        String[] errors = {"canonical_missing", "mirror_conflict", "", "", "", "", ""};
        for (int index = 0; index < codes.length; index++) {
            if (index > 0) {
                advanceTime();
            }
            try (AiMirrorEncryptedOutbox store = open(key())) {
                verifySingleTerminalResponse(store, index, codes[index], errors[index]);
                assertTrue(store.recover().isEmpty());
            }
        }
    }

    private static void verifySingleTerminalResponse(
            AiMirrorEncryptedOutbox store, int index, int status, String error)
            throws Exception {
        var saved = store.save(new DiscordChatPublication(
                GUILD, CHANNEL, "1234567890123456787",
                "rosechat:synthetic:" + index, CANONICAL_TEXT));
        var replay = new AiMirrorReceiptReplay(store, config(), request ->
                new AiMirrorRegistrationPublisher.PostResult(status, error));
        assertEquals(AiMirrorReceiptReplay.Outcome.TERMINAL, replay.attemptOne(saved));
    }

    @Test
    void interruptedNetworkAndFailedAckRetainDurableCount() throws Exception {
        byte[] key = key();
        try (AiMirrorEncryptedOutbox store = open(key)) {
            var saved = store.save(receipt());
            var replay = new AiMirrorReceiptReplay(store, config(), request -> {
                throw new IOException("synthetic network unavailable");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.TRANSIENT,
                    replay.attemptOne(saved));
            assertThrows(IOException.class, () -> store.acknowledge(saved),
                    "stale uncounted receipt must never delete the persisted attempt");
        }
        try (AiMirrorEncryptedOutbox resumed = open(key)) {
            List<AiMirrorEncryptedOutbox.Saved> recovered = resumed.recover();
            assertEquals(1, recovered.size());
            assertEquals(1, recovered.getFirst().recoveryAttempts());
            assertFalse(recovered.getFirst().toString().contains(CANONICAL_TEXT));
        }
    }

    @Test
    void confirmedAliasCannotResetGlobalThrottleForAnotherReceiptAfterRestart()
            throws Exception {
        byte[] key = key();
        try (AiMirrorEncryptedOutbox initial = open(key)) {
            var first = initial.save(receipt());
            initial.save(new DiscordChatPublication(GUILD, CHANNEL,
                    "1234567890123456786", "rosechat:synthetic:second",
                    CANONICAL_TEXT));
            var replay = new AiMirrorReceiptReplay(initial, config(), request ->
                    new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, ""));
            assertEquals(AiMirrorReceiptReplay.Outcome.CONFIRMED, replay.attemptOne(first));
            assertEquals(1, initial.recover().size());
        }
        try (AiMirrorEncryptedOutbox restarted = open(key)) {
            var second = restarted.recover().getFirst();
            var replay = new AiMirrorReceiptReplay(restarted, config(), request -> {
                throw new AssertionError("a restarted worker must not bypass global rate limit");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.DEFERRED, replay.attemptOne(second));
            assertEquals(0, restarted.recover().getFirst().recoveryAttempts());
        }
        advanceTime();
        try (AiMirrorEncryptedOutbox later = open(key)) {
            var second = later.recover().getFirst();
            var replay = new AiMirrorReceiptReplay(later, config(), request ->
                    new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, ""));
            assertEquals(AiMirrorReceiptReplay.Outcome.CONFIRMED, replay.attemptOne(second));
            assertTrue(later.recover().isEmpty());
        }
    }

    @Test
    void corruptedAuthenticatedGlobalRateStateRefusesHttp() throws Exception {
        byte[] key = key();
        try (AiMirrorEncryptedOutbox initial = open(key)) {
            var first = initial.save(receipt());
            var replay = new AiMirrorReceiptReplay(initial, config(), request ->
                    new AiMirrorRegistrationPublisher.PostResult(
                            HTTP_UNAVAILABLE, "storage_unavailable"));
            assertEquals(AiMirrorReceiptReplay.Outcome.TRANSIENT, replay.attemptOne(first));
        }
        Path rate = temporary.resolve(".mirror-rate");
        byte[] encrypted = Files.readAllBytes(rate);
        encrypted[encrypted.length - 1] ^= 1;
        Files.write(rate, encrypted);
        advanceTime();
        try (AiMirrorEncryptedOutbox restarted = open(key)) {
            var saved = restarted.recover().getFirst();
            var replay = new AiMirrorReceiptReplay(restarted, config(), request -> {
                throw new AssertionError("untrusted rate state cannot authorize HTTP");
            });
            assertThrows(IOException.class, () -> replay.attemptOne(saved));
        }
    }

    @Test
    void changedPublicChannelAuthorizationCannotTriggerRecovery() throws Exception {
        try (AiMirrorEncryptedOutbox store = open(key())) {
            var original = store.save(receipt());
            AiMirrorPublicationConfig unrelated = new AiMirrorPublicationConfig(
                    config().endpoint(), "synthetic-bridge", "synthetic-secret",
                    GUILD, Set.of(1234567890123456777L), "discord_general");
            assertThrows(IllegalArgumentException.class, () ->
                    new AiMirrorReceiptReplay(store, unrelated, request -> {
                        throw new AssertionError("HTTP must not run with wrong route scope");
                    }));
            assertEquals(List.of(original), store.recover());
        }
    }

    private static String httpBody(java.net.http.HttpRequest request) throws IOException {
        var publisher = request.bodyPublisher().orElseThrow();
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        publisher.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription handle) {
                handle.request(Long.MAX_VALUE);
            }
            @Override
            public void onNext(java.nio.ByteBuffer data) {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                sink.writeBytes(bytes);
            }
            @Override
            public void onError(Throwable exception) {
                throw new IllegalStateException("synthetic request capture failed");
            }
            @Override
            public void onComplete() {
            }
        });
        return sink.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void advanceTime() {
        simulatedTime = simulatedTime.plusSeconds(61);
    }

    @Test
    void rapidRestartCannotBypassOneMinuteGlobalRecoveryRate() throws Exception {
        byte[] key = key();
        try (AiMirrorEncryptedOutbox initial = open(key)) {
            var saved = initial.save(receipt());
            var replay = new AiMirrorReceiptReplay(initial, config(), request ->
                    new AiMirrorRegistrationPublisher.PostResult(
                            HTTP_UNAVAILABLE, "storage_unavailable"));
            assertEquals(AiMirrorReceiptReplay.Outcome.TRANSIENT, replay.attemptOne(saved));
        }
        try (AiMirrorEncryptedOutbox immediate = open(key)) {
            var restored = immediate.recover().getFirst();
            var replay = new AiMirrorReceiptReplay(immediate, config(), request -> {
                throw new AssertionError("HTTP was not due yet");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.DEFERRED, replay.attemptOne(restored));
            assertEquals(1, immediate.recover().getFirst().recoveryAttempts());
        }
        advanceTime();
        try (AiMirrorEncryptedOutbox later = open(key)) {
            var restored = later.recover().getFirst();
            var replay = new AiMirrorReceiptReplay(later, config(), request ->
                    new AiMirrorRegistrationPublisher.PostResult(HTTP_OK, ""));
            assertEquals(AiMirrorReceiptReplay.Outcome.CONFIRMED, replay.attemptOne(restored));
            assertTrue(later.recover().isEmpty());
        }
    }

    private AiMirrorEncryptedOutbox open(byte[] key) throws IOException {
        return new AiMirrorEncryptedOutbox(
                temporary, key, DESTINATION, GUILD, Set.of(CHANNEL),
                "discord_general", Clock.fixed(simulatedTime, ZoneOffset.UTC));
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-secret", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication receipt() {
        return new DiscordChatPublication(
                GUILD, CHANNEL, "1234567890123456787",
                "rosechat:synthetic:durable", CANONICAL_TEXT);
    }

    private static byte[] key() {
        byte[] material = new byte[32];
        java.util.Arrays.fill(material, (byte) 0x48);
        return material;
    }
}

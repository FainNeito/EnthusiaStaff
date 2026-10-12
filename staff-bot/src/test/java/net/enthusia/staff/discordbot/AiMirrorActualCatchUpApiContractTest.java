package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in actual Java HTTP -> disposable Python FastAPI/SQLite.
 * 70 authenticated failed-first-send receipts recovered with virtual time:
 * exercises real 200/ACK paths without waiting six wall-clock minutes.
 */
class AiMirrorActualCatchUpApiContractTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final int BACKLOG = 70;
    private static final long CATCH_UP_INTERVAL_MILLIS = 5_000L;

    @Test
    void realApiRecoversSeventyBackloggedAliasesBeforeContentTtl(
            @TempDir Path journal) throws Exception {
        URI endpoint = localEndpoint(journal);
        var clock = new VirtualClock(Instant.now().toEpochMilli());
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x48);
        var config = new AiMirrorPublicationConfig(
                endpoint, "synthetic-staffbot-bridge", "synthetic-mirror-only-token",
                GUILD, Set.of(CHANNEL), "discord_general");
        AtomicInteger onlineRequests = new AtomicInteger();
        AtomicBoolean offline = new AtomicBoolean(true);

        try (AiMirrorEncryptedOutbox store = new AiMirrorEncryptedOutbox(
                journal, key, "staging:synthetic-catchup:127.0.0.1",
                GUILD, Set.of(CHANNEL), "discord_general", clock)) {
            for (int index = 0; index < BACKLOG; index++) {
                store.save(publication(index));
            }
            var replay = new AiMirrorReceiptReplay(
                    store, config, networkPoster(offline, onlineRequests));
            verifyOutageThenCatchUp(store, replay, clock, offline, onlineRequests);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static URI localEndpoint(Path journal) throws IOException {
        String configured = System.getenv("ENTHUSIA_AI_SYNTHETIC_MIRROR_URL");
        assumeTrue(configured != null && !configured.isBlank(),
                "isolated local Python contract fixture must opt in");
        URI endpoint = URI.create(configured);
        assertEquals("http", endpoint.getScheme());
        assertEquals("127.0.0.1", endpoint.getHost());
        assertEquals("/v1/mirror-aliases", endpoint.getRawPath());
        assertTrue(endpoint.getPort() > 0);
        if (Files.getFileStore(journal).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    journal, PosixFilePermissions.fromString("rwx------"));
        }
        return endpoint;
    }

    private static AiMirrorRegistrationPublisher.DetailedPoster networkPoster(
            AtomicBoolean offline, AtomicInteger requests) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return request -> {
            if (offline.get()) {
                throw new IOException("synthetic disconnected loopback");
            }
            requests.incrementAndGet();
            HttpResponse<Void> response = client.send(
                    request, HttpResponse.BodyHandlers.discarding());
            return new AiMirrorRegistrationPublisher.PostResult(
                    response.statusCode(),
                    response.headers().firstValue("X-Moderation-Mirror-Error").orElse(""));
        };
    }

    private static void verifyOutageThenCatchUp(
            AiMirrorEncryptedOutbox store, AiMirrorReceiptReplay replay,
            VirtualClock clock, AtomicBoolean offline, AtomicInteger requests)
            throws Exception {
        var first = store.recover().getFirst();
        assertEquals(AiMirrorReceiptReplay.Outcome.TRANSIENT,
                replay.attemptOne(first));
        assertEquals(60_000L, store.recoveryPollMillis());
        assertEquals(0, requests.get());
        assertEquals(BACKLOG, store.recover().size());
        clock.advanceMillis(60_001L);
        offline.set(false);
        for (int index = 0; index < BACKLOG; index++) {
            var selected = store.recover().getFirst();
            assertEquals(AiMirrorReceiptReplay.Outcome.CONFIRMED,
                    replay.attemptOne(selected));
            assertEquals(CATCH_UP_INTERVAL_MILLIS, store.recoveryPollMillis());
            if (index + 1 < BACKLOG) {
                clock.advanceMillis(CATCH_UP_INTERVAL_MILLIS);
            }
        }
        assertEquals(BACKLOG, requests.get());
        assertEquals(0, store.expiredRecordCount());
        assertTrue(store.recover().isEmpty());
    }

    private static DiscordChatPublication publication(int index) {
        return new DiscordChatPublication(
                GUILD, CHANNEL, Long.toString(1234567890123455000L + index),
                "rosechat:canonical:catchup-network-" + index,
                "synthetic recovered content " + index);
    }

    private static final class VirtualClock extends Clock {
        private final AtomicLong now;

        private VirtualClock(long epochMillis) {
            now = new AtomicLong(epochMillis);
        }

        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }

        private void advanceMillis(long millis) {
            now.addAndGet(millis);
        }
    }
}

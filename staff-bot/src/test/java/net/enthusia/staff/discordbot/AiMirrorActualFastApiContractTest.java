package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Explicitly opt-in disposable Java -> actual Python FastAPI/SQLite test.
 * Fixture pre-creates 70 distinct FINAL canonical Minecraft source events.
 * No JDA, real tokens, model inference, deletion or sanctions.
 */
class AiMirrorActualFastApiContractTest {
    private static final String ENDPOINT = "ENTHUSIA_AI_SYNTHETIC_MIRROR_URL";
    private static final int COUNT = 70;
    private static final int HTTP_OK = 200;
    private static final int TIMEOUT_SECONDS = 45;
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;

    @Test
    void actualFastApiAcceptsSeventyPostFsyncMirrorsWithoutSlowMinuteQuota(
            @TempDir Path journal) throws Exception {
        String configured = System.getenv(ENDPOINT);
        assumeTrue(configured != null && !configured.isBlank(),
                "disposable FastAPI fixture must opt in");
        URI endpoint = URI.create(configured);
        assertEquals("http", endpoint.getScheme());
        assertEquals("127.0.0.1", endpoint.getHost());
        assertEquals("/v1/mirror-aliases", endpoint.getRawPath());
        assertTrue(endpoint.getPort() > 0);
        if (Files.getFileStore(journal).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(journal,
                    PosixFilePermissions.fromString("rwx------"));
        }
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x48);
        AiMirrorPublicationConfig config = new AiMirrorPublicationConfig(
                endpoint, "synthetic-staffbot-bridge", "synthetic-mirror-only-token",
                GUILD, Set.of(CHANNEL), "discord_general");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();

        try (AiMirrorEncryptedOutbox store = new AiMirrorEncryptedOutbox(
                    journal, key, "staging:synthetic-fast:127.0.0.1",
                    GUILD, Set.of(CHANNEL), "discord_general", Clock.systemUTC());
                AiMirrorDurableRecoverySession session = new AiMirrorDurableRecoverySession(
                    new FakeChat(), store, config, request -> {
                        HttpResponse<Void> response = client.send(
                                request, HttpResponse.BodyHandlers.discarding());
                        return new AiMirrorRegistrationPublisher.PostResult(
                                response.statusCode(), response.headers()
                                        .firstValue("X-Moderation-Mirror-Error").orElse(""));
                    })) {
            session.start();
            session.resume();
            for (int i = 0; i < COUNT; i++) {
                session.published(new DiscordChatPublication(
                        GUILD, CHANNEL, Long.toString(1234567890123456000L + i),
                        "rosechat:canonical:fast-network-" + i,
                        "synthetic fast registration " + i));
                Thread.sleep(55);
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (session.immediateStats().confirmed() + session.recoveryStats().confirmed()
                    < COUNT && System.nanoTime() < deadline) {
                Thread.sleep(15);
            }
            assertEquals(COUNT, session.ingressStats().persisted());
            assertEquals(0, session.ingressStats().capacityDrops());
            assertEquals(COUNT, session.immediateStats().confirmed()
                    + session.recoveryStats().confirmed());
            assertTrue(session.immediateStats().confirmed() >= COUNT - 1);
            assertTrue(store.recover().isEmpty());
            assertTrue(!session.unhealthy());
        }
    }

    private static final class FakeChat implements StaffBotChatLifecycle {
        @Override public void start() { }
        @Override public void resume() { }
        @Override public void pause() { }
        @Override public void close() { }
    }
}

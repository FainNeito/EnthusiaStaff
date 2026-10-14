package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

/**
 * Disposable opt-in two-JVM integration contract. Uses only a real Python API
 * listening on 127.0.0.1, synthetic credentials, and a throwaway journal.
 * Never loads StaffBotRuntime, JDA, actual Minecraft or Discord credentials.
 */
public final class AiMirrorActualHttpCrashProbe {
    private static final String ENDPOINT_ENV = "ENTHUSIA_AI_SYNTHETIC_MIRROR_URL";
    private static final String SAVE_PHASE = "commit-then-exit";
    private static final String RECOVER_PHASE = "reopen-and-replay";
    private static final String EXPECTED_PATH = "/v1/mirror-aliases";
    private static final String BINDING = "staging:synthetic:real-http-process-crash";
    private static final String CLIENT = "synthetic-staffbot-bridge";
    private static final String TOKEN = "synthetic-mirror-only-token";
    private static final String CANONICAL = "rosechat:canonical:process-crash-test";
    private static final String ORIGINAL = "Minecraft PvP café 💎";
    private static final String DISCORD_ID = "1234567890123456785";
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final int EXPECTED_ARGUMENTS = 2;
    private static final int HTTP_OK = 200;
    private static final int EXPECTED_REFS = 2;
    private static final String EXPECTED_ACTION = "ALLOW";
    private static final String EXPECTED_CONTAINMENT = "NONE";
    private static final int KEY_LENGTH = 32;
    private static final Duration HTTP_DEADLINE = Duration.ofSeconds(2);
    private static final ObjectMapper JSON = new ObjectMapper();

    private AiMirrorActualHttpCrashProbe() {}

    public static void main(String[] args) throws Exception {
        validateArguments(args);
        URI uri = verifiedLoopbackUri();
        Path directory = privateJournal(Path.of(args[1]));
        byte[] key = new byte[KEY_LENGTH];
        Arrays.fill(key, (byte) 0x48); // Synthetic test fixture only.
        try {
            AiMirrorPublicationConfig config = new AiMirrorPublicationConfig(
                    uri, CLIENT, TOKEN, GUILD, Set.of(CHANNEL), "discord_general");
            if (SAVE_PHASE.equals(args[0])) {
                commitThenExit(directory, key, config);
            } else {
                reopenAndReplay(directory, key, config);
            }
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static void validateArguments(String[] args) {
        if (args.length != EXPECTED_ARGUMENTS
                || (!SAVE_PHASE.equals(args[0]) && !RECOVER_PHASE.equals(args[0]))) {
            throw new IllegalArgumentException("unknown synthetic HTTP crash probe phase");
        }
    }

    private static URI verifiedLoopbackUri() {
        String configured = System.getenv(ENDPOINT_ENV);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("disposable loopback API fixture is not configured");
        }
        URI uri = URI.create(configured);
        if (!validLoopbackAuthority(uri) || !validExactPath(uri)) {
            throw new IllegalArgumentException("non-loopback synthetic HTTP target rejected");
        }
        return uri;
    }

    private static boolean validLoopbackAuthority(URI uri) {
        return "http".equals(uri.getScheme())
                && "127.0.0.1".equals(uri.getHost())
                && uri.getPort() > 0 && uri.getUserInfo() == null;
    }

    private static boolean validExactPath(URI uri) {
        return EXPECTED_PATH.equals(uri.getRawPath())
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    private static Path privateJournal(Path directory) throws Exception {
        Files.createDirectories(directory);
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    PosixFilePermissions.fromString("rwx------"));
        }
        return directory;
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(HTTP_DEADLINE)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static void commitThenExit(
            Path journal, byte[] key, AiMirrorPublicationConfig config) throws Exception {
        AiMirrorEncryptedOutbox outbox = open(journal, key, Clock.systemUTC());
        DiscordChatPublication source = new DiscordChatPublication(
                GUILD, CHANNEL, DISCORD_ID, CANONICAL, ORIGINAL);
        var saved = outbox.save(source);
        var prepared = outbox.prepareRecoveryAttempt(saved);
        if (prepared.status() != AiMirrorEncryptedOutbox.AttemptStatus.READY) {
            throw new IllegalStateException("first synthetic recovery attempt was not authorized");
        }
        HttpResponse<String> reply = client().send(
                AiMirrorRegistrationPublisher.request(config, prepared.saved().publication()),
                HttpResponse.BodyHandlers.ofString());
        verifyReply(reply, null);
        // Deliberately exit the JVM with the journal open and the HTTP alias
        // committed but unacknowledged. Gradle reports nonzero by design.
        throw new IllegalStateException("synthetic crash after HTTP commit before ACK");
    }

    private static void reopenAndReplay(
            Path journal, byte[] key, AiMirrorPublicationConfig config) throws Exception {
        Clock afterCooldown = Clock.offset(Clock.systemUTC(), Duration.ofSeconds(61));
        try (AiMirrorEncryptedOutbox outbox = open(journal, key, afterCooldown)) {
            var records = outbox.recover();
            if (records.size() != 1 || records.getFirst().recoveryAttempts() != 1
                    || !CANONICAL.equals(records.getFirst().publication().canonicalMessageId())) {
                throw new IllegalStateException("synthetic unacknowledged receipt was not recovered");
            }
            var replay = new AiMirrorReceiptReplay(outbox, config, request -> {
                HttpResponse<String> response = client().send(
                        request, HttpResponse.BodyHandlers.ofString());
                verifyReply(response, true);
                return new AiMirrorRegistrationPublisher.PostResult(
                        response.statusCode(),
                        response.headers().firstValue("X-Moderation-Mirror-Error").orElse(""));
            });
            if (replay.attemptOne(records.getFirst()) != AiMirrorReceiptReplay.Outcome.CONFIRMED
                    || !outbox.recover().isEmpty()) {
                throw new IllegalStateException("synthetic replay did not acknowledge receipt");
            }
        }
    }

    private static AiMirrorEncryptedOutbox open(Path journal, byte[] key, Clock clock)
            throws Exception {
        return new AiMirrorEncryptedOutbox(journal, key, BINDING,
                GUILD, Set.of(CHANNEL), "discord_general", clock);
    }

    private static void verifyReply(HttpResponse<String> response, Boolean expectedReplay)
            throws IOException {
        if (response.statusCode() != HTTP_OK) {
            throw new IllegalStateException("synthetic API did not confirm exact mirror alias");
        }
        JsonNode data = JSON.readTree(response.body());
        if (expectedReplay != null
                && data.path("idempotent_replay").asBoolean() != expectedReplay) {
            throw new IllegalStateException("synthetic alias replay flag mismatch");
        }
        if (data.path("related_messages").size() != EXPECTED_REFS) {
            throw new IllegalStateException("synthetic alias reference count mismatch");
        }
        if (!EXPECTED_ACTION.equals(data.path("message_action").asText())) {
            throw new IllegalStateException("synthetic alias action contract mismatch");
        }
        if (!EXPECTED_CONTAINMENT.equals(data.path("containment").asText())) {
            throw new IllegalStateException("synthetic alias containment mismatch");
        }
    }
}

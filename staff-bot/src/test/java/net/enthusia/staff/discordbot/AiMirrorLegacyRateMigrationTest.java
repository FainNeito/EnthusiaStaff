package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exactly the legacy encrypted v2 rate JSON, with disposable fixture key. */
class AiMirrorLegacyRateMigrationTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String DESTINATION = "staging:synthetic-legacy-rate";
    private static final Instant FIRST = Instant.parse("2026-10-12T01:00:00Z");
    @TempDir Path directory;

    @BeforeEach
    void privateDirectory() throws Exception {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    directory, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void oldEncryptedRateFileRemainsConservativeUntilConfirmedNewAck()
            throws Exception {
        byte[] key = key();
        writeOldRateState(key);
        try (AiMirrorEncryptedOutbox store = open(key, FIRST.plusSeconds(30))) {
            assertEquals(60_000L, store.recoveryPollMillis());
            var saved = store.save(publication());
            var replay = new AiMirrorReceiptReplay(store, config(), request -> {
                throw new AssertionError("legacy v2 cooldown must block HTTP");
            });
            assertEquals(AiMirrorReceiptReplay.Outcome.DEFERRED, replay.attemptOne(saved));
        }
        try (AiMirrorEncryptedOutbox store = open(key, FIRST.plusSeconds(61))) {
            var saved = store.recover().getFirst();
            var replay = new AiMirrorReceiptReplay(store, config(), request ->
                    new AiMirrorRegistrationPublisher.PostResult(200, ""));
            assertEquals(AiMirrorReceiptReplay.Outcome.CONFIRMED, replay.attemptOne(saved));
            assertTrue(store.recover().isEmpty());
            assertEquals(5_000L, store.recoveryPollMillis());
        }
        Arrays.fill(key, (byte) 0);
    }

    private void writeOldRateState(byte[] key) throws Exception {
        String json = "{\"version\":2,\"kind\":\"mirror-recovery\","
                + "\"guildId\":" + GUILD
                + ",\"profile\":\"discord_general\",\"lastAttemptMillis\":"
                + FIRST.toEpochMilli() + "}";
        byte[] nonce = new byte[12];
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"),
                new IvParameterSpec(nonce));
        byte[] magic = {'E', 'M', 'R', '2'};
        cipher.updateAAD(magic);
        cipher.updateAAD(DESTINATION.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
        byte[] data = ByteBuffer.allocate(magic.length + nonce.length + encrypted.length)
                .put(magic).put(nonce).put(encrypted).array();
        Path rate = directory.resolve(".mirror-rate");
        Files.write(rate, data);
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    rate, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private AiMirrorEncryptedOutbox open(byte[] key, Instant time) throws Exception {
        return new AiMirrorEncryptedOutbox(directory, key, DESTINATION,
                GUILD, Set.of(CHANNEL), "discord_general",
                Clock.fixed(time, ZoneOffset.UTC));
    }

    private static byte[] key() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x48);
        return key;
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-bridge", "synthetic-token", GUILD,
                Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication publication() {
        return new DiscordChatPublication(GUILD, CHANNEL,
                "1234567890123456787", "rosechat:synthetic:legacy-v2",
                "synthetic original text");
    }
}

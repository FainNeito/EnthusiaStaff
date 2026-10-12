package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** No environment variables are modified and no real services are contacted. */
class AiMirrorDurableStagingSettingsTest {
    private static final long GUILD = 1410303324745371709L;
    private static final long CHANNEL = 1541286004298752091L;
    private static final String TEST_KEY = "synthetic-fixed-key-content";
    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temp;

    @Test
    void missingToggleLeavesTheWholeRecoveryFeatureDisabled() throws Exception {
        assertTrue(AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(config()), Map.of()).isEmpty());
        assertTrue(AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.PRODUCTION, Optional.empty(), Map.of()).isEmpty());
    }

    @Test
    void productionOrAbsentRegistrationNeverAcceptsDurableToggle() {
        Map<String, String> enabled = Map.of(
                AiMirrorDurableStagingSettings.ENABLED, "true");
        assertThrows(IllegalArgumentException.class,
                () -> AiMirrorDurableStagingSettings.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, Optional.of(config()), enabled));
        assertThrows(IllegalArgumentException.class,
                () -> AiMirrorDurableStagingSettings.fromEnvironment(
                        StaffBotEnvironment.STAGING, Optional.empty(), enabled));
        assertThrows(IllegalArgumentException.class,
                () -> AiMirrorDurableStagingSettings.fromEnvironment(
                        StaffBotEnvironment.STAGING, Optional.of(config()), Map.of(
                                AiMirrorDurableStagingSettings.ENABLED, "unexpected")));
    }

    @Test
    void missingAndRelativePathsFailBeforeAnyJournalWrite() {
        Map<String, String> enabled = Map.of(
                AiMirrorDurableStagingSettings.ENABLED, "true");
        assertThrows(IllegalArgumentException.class,
                () -> AiMirrorDurableStagingSettings.fromEnvironment(
                        StaffBotEnvironment.STAGING, Optional.of(config()), enabled));
        assertThrows(IllegalArgumentException.class,
                () -> AiMirrorDurableStagingSettings.fromEnvironment(
                        StaffBotEnvironment.STAGING, Optional.of(config()), Map.of(
                                AiMirrorDurableStagingSettings.ENABLED, "true",
                                AiMirrorDurableStagingSettings.JOURNAL_DIRECTORY, "relative-dir",
                                AiMirrorDurableStagingSettings.JOURNAL_KEY_FILE, "relative-key")));
    }

    @Test
    void keyFilePermissionsMustBePrivateAndKeySizeExact() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path directory = temp.resolve("journal");
        Files.createDirectory(directory);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        Path key = temp.resolve("key");
        Files.write(key, fixedKey());
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-r--r--"));
        var env = settings(directory, key);
        assertThrows(IOException.class, () -> AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(config()), env));
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        Files.write(key, new byte[31]);
        assertThrows(IOException.class, () -> AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(config()), env));
    }

    @Test
    void correctPreProvisionedLinuxStorageCanReopenEncryptedReceipts() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path directory = temp.resolve("journal");
        Files.createDirectory(directory);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        Path key = temp.resolve("key");
        Files.write(key, fixedKey());
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("r--------"));
        var settings = AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(config()), settings(directory, key));
        assertTrue(settings.isPresent());
        try (AiMirrorEncryptedOutbox outbox = settings.orElseThrow().openOutbox(FIXED)) {
            outbox.save(receipt());
            assertEquals(1, outbox.recover().size());
        }
        try (AiMirrorEncryptedOutbox reopened = settings.orElseThrow().openOutbox(FIXED)) {
            assertEquals(1, reopened.recover().size());
        }
    }

    @Test
    void changedEndpointCannotDecryptExistingJournalOnLinux() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path directory = provisionDirectory();
        Path key = provisionKey();
        var settings = AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(config()), settings(directory, key))
                .orElseThrow();
        try (AiMirrorEncryptedOutbox first = settings.openOutbox(FIXED)) {
            first.save(receipt());
        }
        AiMirrorPublicationConfig otherEndpoint = new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23820/v1/mirror-aliases"),
                "synthetic-client", TEST_KEY, GUILD, Set.of(CHANNEL), "discord_general");
        var mismatched = AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(otherEndpoint), settings(directory, key))
                .orElseThrow();
        try (AiMirrorEncryptedOutbox different = mismatched.openOutbox(FIXED)) {
            assertThrows(IOException.class, different::recover);
        }
    }

    @Test
    void symlinkedKeyIsRejectedBeforeReadingSecretsOnLinux() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path directory = provisionDirectory();
        Path key = provisionKey();
        Path link = temp.resolve("link-to-secret");
        Files.createSymbolicLink(link, key);
        assertThrows(IOException.class, () -> AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(config()), settings(directory, link)));
    }

    private Path provisionDirectory() throws IOException {
        Path directory = temp.resolve("journal");
        Files.createDirectory(directory);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        return directory;
    }

    private Path provisionKey() throws IOException {
        Path key = temp.resolve("key");
        Files.write(key, fixedKey());
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("r--------"));
        return key;
    }

    @Test
    void refusingNonPosixACLsIsExplicitRatherThanAssumingSecureStorage()
            throws Exception {
        Assumptions.assumeFalse(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path directory = temp.resolve("journal");
        Files.createDirectory(directory);
        Path key = temp.resolve("key");
        Files.write(key, fixedKey());
        assertThrows(IOException.class, () -> AiMirrorDurableStagingSettings.fromEnvironment(
                StaffBotEnvironment.STAGING, Optional.of(config()), settings(directory, key)));
    }

    private static Map<String, String> settings(Path directory, Path key) {
        return Map.of(
                AiMirrorDurableStagingSettings.ENABLED, "true",
                AiMirrorDurableStagingSettings.JOURNAL_DIRECTORY, directory.toString(),
                AiMirrorDurableStagingSettings.JOURNAL_KEY_FILE, key.toString());
    }

    private static AiMirrorPublicationConfig config() {
        return new AiMirrorPublicationConfig(
                URI.create("http://127.0.0.1:23819/v1/mirror-aliases"),
                "synthetic-client", TEST_KEY, GUILD, Set.of(CHANNEL), "discord_general");
    }

    private static DiscordChatPublication receipt() {
        return new DiscordChatPublication(GUILD, CHANNEL, "1234567890123456787",
                "rosechat:synthetic:config", "synthetic public text");
    }

    private static byte[] fixedKey() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x48);
        return key;
    }
}

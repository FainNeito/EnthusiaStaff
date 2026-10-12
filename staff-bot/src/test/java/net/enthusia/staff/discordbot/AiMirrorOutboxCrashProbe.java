package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;

/**
 * Standalone, synthetic JVM helper: intentionally terminates abruptly after
 * fsync to exercise durable journal recovery in a different process.
 */
public final class AiMirrorOutboxCrashProbe {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String DESTINATION = "staging:synthetic-bridge:example.invalid:443";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);

    private AiMirrorOutboxCrashProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || (!"save-and-crash".equals(args[0])
                && !"reopen-and-ack".equals(args[0]))) {
            System.exit(2);
        }
        Path directory = Path.of(args[1]);
        Files.createDirectories(directory);
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    PosixFilePermissions.fromString("rwx------"));
        }
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x48); // Test-only key; no production secret.
        try (AiMirrorEncryptedOutbox journal = new AiMirrorEncryptedOutbox(
                directory, key, DESTINATION, GUILD, Set.of(CHANNEL),
                "discord_general", CLOCK)) {
            if ("save-and-crash".equals(args[0])) {
                journal.save(new DiscordChatPublication(
                        GUILD, CHANNEL, "1234567890123456787",
                        "rosechat:synthetic:jvm-crash", "synthetic message only"));
                // Force no Java finally/close: OS must release process lock.
                Runtime.getRuntime().halt(0);
            }
            var entries = journal.recover();
            if (entries.size() != 1 || !entries.getFirst().publication()
                    .canonicalMessageId().equals("rosechat:synthetic:jvm-crash")) {
                System.exit(3);
            }
            journal.acknowledge(entries.getFirst());
            if (!journal.recover().isEmpty()) {
                System.exit(4);
            }
        }
    }
}

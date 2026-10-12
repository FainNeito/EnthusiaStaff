package net.enthusia.staff.discordbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;

/** Two fixed, synthetic JVM phases; no shell, Discord, or external network. */
public final class AiMirrorOutboxCrashProbe {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final String DESTINATION = "staging:synthetic-bridge:example.invalid:443";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
    private static final String CANONICAL_ID = "rosechat:synthetic:jvm-crash";

    private AiMirrorOutboxCrashProbe() {
    }

    public static void main(String[] args) throws Exception {
        validateArguments(args);
        Path directory = preparePrivateDirectory(Path.of(args[1]));
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x48); // Fixed synthetic test-only key.
        try (AiMirrorEncryptedOutbox journal = new AiMirrorEncryptedOutbox(
                directory, key, DESTINATION, GUILD, Set.of(CHANNEL),
                "discord_general", CLOCK)) {
            if ("save-and-crash".equals(args[0])) {
                crashAfterCommit(journal);
            } else {
                recoverAndAcknowledge(journal);
            }
        }
    }

    private static void validateArguments(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("invalid synthetic phase argument count");
        }
        if (!"save-and-crash".equals(args[0]) && !"reopen-and-ack".equals(args[0])) {
            throw new IllegalArgumentException("invalid synthetic phase");
        }
    }

    private static Path preparePrivateDirectory(Path directory) throws Exception {
        Files.createDirectories(directory);
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory,
                    PosixFilePermissions.fromString("rwx------"));
        }
        return directory;
    }

    private static void crashAfterCommit(AiMirrorEncryptedOutbox journal) throws Exception {
        journal.save(new DiscordChatPublication(
                GUILD, CHANNEL, "1234567890123456787", CANONICAL_ID,
                "synthetic message only"));
        // Deliberately bypass AutoCloseable: verify OS lock release after a hard crash.
        Runtime.getRuntime().halt(0);
    }

    private static void recoverAndAcknowledge(AiMirrorEncryptedOutbox journal)
            throws Exception {
        var entries = journal.recover();
        if (entries.size() != 1
                || !CANONICAL_ID.equals(entries.getFirst().publication().canonicalMessageId())) {
            throw new IllegalStateException("hard-crash receipt was not recovered");
        }
        journal.acknowledge(entries.getFirst());
        if (!journal.recover().isEmpty()) {
            throw new IllegalStateException("acknowledged receipt was not removed");
        }
    }
}

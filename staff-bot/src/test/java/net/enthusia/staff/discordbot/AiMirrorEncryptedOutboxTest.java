package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic-at-rest tests only: no runtime registration, Discord or live API. */
class AiMirrorEncryptedOutboxTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;
    private static final long OUTSIDE_CHANNEL = 1234567890123456777L;
    private static final String BINDING = "staging:synthetic-bridge:example.invalid:443";
    private static final Instant TIME = Instant.parse("2026-10-12T01:00:00Z");

    @TempDir Path temp;

    @BeforeEach
    void createPrivateTestJournal() throws IOException {
        if (Files.getFileStore(temp).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(temp,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void encryptedReceiptSurvivesOwnerRestartAndIsOnlyRemovedAfterAck() throws Exception {
        byte[] key = key();
        AiMirrorEncryptedOutbox.Saved saved;
        try (AiMirrorEncryptedOutbox outbox = open(key, BINDING, fixed(TIME))) {
            saved = outbox.save(receipt(1));
            Path file = temp.resolve(saved.fileName());
            assertTrue(Files.isRegularFile(file));
            byte[] bytes = Files.readAllBytes(file);
            String diskView = new String(bytes, StandardCharsets.ISO_8859_1);
            assertFalse(diskView.contains("original private test phrase"));
            assertFalse(diskView.contains("rosechat:original:1"));
            assertFalse(saved.fileName().contains("123456"));
            assertFalse(saved.toString().contains("original private test phrase"));
            assertEquals(List.of(saved), outbox.recover());
        }
        try (AiMirrorEncryptedOutbox restarted = open(key, BINDING, fixed(TIME.plusSeconds(25)))) {
            assertEquals(List.of(saved), restarted.recover());
            restarted.acknowledge(saved);
            assertTrue(restarted.recover().isEmpty());
        }
    }

    @Test
    void boundedWritesHaveDistinctRandomChaCha20Poly1305Nonces() throws Exception {
        Set<String> seen = new java.util.HashSet<>();
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            for (int index = 0; index < 128; index++) {
                AiMirrorEncryptedOutbox.Saved saved = outbox.save(receipt(index));
                byte[] sealed = Files.readAllBytes(temp.resolve(saved.fileName()));
                assertTrue(sealed[0] == 'E' && sealed[1] == 'M'
                        && sealed[2] == 'R' && sealed[3] == '2');
                String nonce = java.util.HexFormat.of().formatHex(sealed, 4, 16);
                assertTrue(seen.add(nonce), "ChaCha20-Poly1305 nonce reused in bounded synthetic writes");
            }
        }
        assertEquals(128, seen.size());
    }

    @Test
    void replayWithDifferentKeyOrDestinationCannotReadStoredData() throws Exception {
        byte[] key = key();
        try (AiMirrorEncryptedOutbox outbox = open(key, BINDING, fixed(TIME))) {
            outbox.save(receipt(1));
        }
        try (AiMirrorEncryptedOutbox wrongKey = open(otherKey(), BINDING, fixed(TIME))) {
            assertThrows(IOException.class, wrongKey::recover);
        }
        try (AiMirrorEncryptedOutbox wrongDestination = open(
                key, BINDING + ":different", fixed(TIME))) {
            assertThrows(IOException.class, wrongDestination::recover);
        }
        try (AiMirrorEncryptedOutbox original = open(key, BINDING, fixed(TIME))) {
            assertEquals(1, original.recover().size());
        }
    }

    @Test
    void alteredCiphertextNeverProducesAnUnverifiedReceipt() throws Exception {
        AiMirrorEncryptedOutbox.Saved saved;
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            saved = outbox.save(receipt(2));
        }
        Path file = temp.resolve(saved.fileName());
        byte[] encrypted = Files.readAllBytes(file);
        encrypted[encrypted.length - 1] ^= 0x20;
        Files.write(file, encrypted);
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            assertThrows(IOException.class, outbox::recover);
        }
    }

    @Test
    void expiredReceiptsAreDiscardedInsteadOfReplayed() throws Exception {
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            outbox.save(receipt(1));
        }
        try (AiMirrorEncryptedOutbox restarted = open(
                key(), BINDING, fixed(TIME.plusSeconds(3601)))) {
            assertTrue(restarted.recover().isEmpty());
            assertEquals(1L, restarted.expiredRecordCount());
            assertTrue(restarted.recover().isEmpty());
            assertEquals(1L, restarted.expiredRecordCount(),
                    "expiry must be counted only once");
        }
        try (AiMirrorEncryptedOutbox restarted = open(key(), BINDING, fixed(TIME))) {
            assertTrue(restarted.recover().isEmpty());
        }
    }

    @Test
    void expiredReceiptRejectedDuringAttemptPreparationIsCounted() throws Exception {
        AiMirrorEncryptedOutbox.Saved receipt;
        try (AiMirrorEncryptedOutbox initial = open(key(), BINDING, fixed(TIME))) {
            receipt = initial.save(receipt(2));
        }
        try (AiMirrorEncryptedOutbox restarted = open(
                key(), BINDING, fixed(TIME.plusSeconds(3601)))) {
            var prepared = restarted.prepareRecoveryAttempt(receipt);
            assertEquals(AiMirrorEncryptedOutbox.AttemptStatus.EXHAUSTED,
                    prepared.status());
            assertEquals(1L, restarted.expiredRecordCount());
            assertTrue(restarted.recover().isEmpty());
            assertEquals(1L, restarted.expiredRecordCount());
        }
    }

    @Test
    void onlyOwnerMayOpenTheSameJournalAtOnce() throws Exception {
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            assertThrows(IOException.class, () -> open(key(), BINDING, fixed(TIME)));
            outbox.save(receipt(1));
        }
        try (AiMirrorEncryptedOutbox reopened = open(key(), BINDING, fixed(TIME))) {
            assertEquals(1, reopened.recover().size());
        }
    }

    @Test
    void publicScopeIsValidatedBeforeAnyWrite() throws Exception {
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            assertThrows(IllegalArgumentException.class, () -> outbox.save(
                    new DiscordChatPublication(GUILD, OUTSIDE_CHANNEL,
                            "1234567890123456787", "rosechat:other", "unauthorized test")));
            assertEquals(0, outbox.recover().size());
        }
    }

    @Test
    void boundedRecordQuotaFailsClosedWithoutDiscardingOldRecords() throws Exception {
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            for (int i = 0; i < 128; i++) {
                outbox.save(receipt(i));
            }
            assertThrows(IOException.class, () -> outbox.save(receipt(129)));
            assertEquals(128, outbox.recover().size());
        }
    }

    @Test
    void unexpectedUnencryptedJunkInPrivateJournalIsNotSilentlyIgnored() throws Exception {
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            Files.writeString(temp.resolve("not-a-receipt"), "plaintext");
            assertThrows(IOException.class, outbox::recover);
            assertThrows(IOException.class, () -> outbox.save(receipt(1)));
        }
    }

    @Test
    void foreignSymlinkInsideJournalFailsClosedOnPosix() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path outside = Files.createTempFile("synthetic-receipt-outside-", ".txt");
        try {
            Files.createSymbolicLink(temp.resolve("not-a-receipt"), outside);
            try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
                assertThrows(IOException.class, outbox::recover);
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void interruptedTemporaryWriteCannotBeMistakenForACommittedReceipt() throws Exception {
        AiMirrorEncryptedOutbox.Saved saved;
        try (AiMirrorEncryptedOutbox outbox = open(key(), BINDING, fixed(TIME))) {
            saved = outbox.save(receipt(1));
        }
        Path tempFile = temp.resolve(saved.fileName() + ".tmp");
        Files.writeString(tempFile, "incomplete and unauthenticated write");
        try (AiMirrorEncryptedOutbox restored = open(key(), BINDING, fixed(TIME))) {
            assertEquals(List.of(saved), restored.recover());
            assertFalse(Files.exists(tempFile));
        }
    }

    private AiMirrorEncryptedOutbox open(byte[] keyMaterial, String destination, Clock clock)
            throws IOException {
        return new AiMirrorEncryptedOutbox(
                temp, keyMaterial, destination, GUILD, Set.of(CHANNEL),
                "discord_general", clock);
    }

    private static Clock fixed(Instant time) {
        return Clock.fixed(time, ZoneOffset.UTC);
    }

    private static byte[] key() {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) 0x48);
        return bytes;
    }

    private static byte[] otherKey() {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) 0x37);
        return bytes;
    }

    private static DiscordChatPublication receipt(int index) {
        return new DiscordChatPublication(GUILD, CHANNEL, "1234567890123456787",
                "rosechat:original:" + index, "original private test phrase " + index);
    }
}

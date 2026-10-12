package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Isolated staging prototype: an authenticated, bounded receipt store.
 *
 * <p>NOT integrated with the publisher or its environment configuration yet.
 * No Discord/JDA thread may call this disk-bound component. This alone does
 * not implement recovery or authorize any deletion/enforcement.</p>
 */
final class AiMirrorEncryptedOutbox implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] MAGIC = {'E', 'M', 'R', '1'};
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_RECORDS = 128;
    private static final int MAX_TOTAL_BYTES = 1024 * 1024;
    private static final int MAX_FILE_BYTES = 32 * 1024;
    private static final long TTL_MILLIS = 60 * 60 * 1000L;
    private static final String EXTENSION = ".mir";
    private static final String POSIX_VIEW = "posix";
    private static final String LOCK_NAME = ".mirror.lock";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final FileAttribute<Set<PosixFilePermission>> PRIVATE_FILE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    record Saved(String fileName, DiscordChatPublication publication, long createdMillis) {
        @Override
        public String toString() {
            // An accidental log of the wrapper must never print original chat.
            return "Saved[receipt=REDACTED]";
        }
    }

    private record Sealed(
            int version, long createdMillis, long guildId, long channelId,
            String discordMessageId, String canonicalMessageId,
            String canonicalPlainText, String channelProfile) {}

    private final Path directory;
    private final SecretKey key;
    private final byte[] binding;
    private final long guildId;
    private final Set<Long> channels;
    private final String profile;
    private final Clock clock;
    private final FileChannel lockChannel;
    private final FileLock exclusiveLock;
    private boolean closed;

    AiMirrorEncryptedOutbox(
            Path directory, byte[] keyMaterial, String destinationBinding,
            long guildId, Set<Long> channels, String profile, Clock clock) throws IOException {
        validateSettings(directory, keyMaterial, destinationBinding,
                guildId, channels, profile, clock);
        this.clock = clock;
        this.directory = directory.toAbsolutePath().normalize();
        validateDirectory();
        this.key = new SecretKeySpec(keyMaterial, "AES");
        this.binding = destinationBinding.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.guildId = guildId;
        this.channels = Set.copyOf(channels);
        this.profile = profile;
        Ownership ownership = acquireOwnership();
        lockChannel = ownership.channel();
        exclusiveLock = ownership.lock();
    }

    private record Ownership(FileChannel channel, FileLock lock) {}

    private static void validateSettings(
            Path directory, byte[] keyMaterial, String destinationBinding,
            long guildId, Set<Long> channels, String profile, Clock clock) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(keyMaterial, "keyMaterial");
        Objects.requireNonNull(destinationBinding, "destinationBinding");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(clock, "clock");
        if (!validIdentitySettings(keyMaterial, destinationBinding, guildId)) {
            throw new IllegalArgumentException("invalid isolated receipt store settings");
        }
        if (channels.isEmpty() || channels.stream().anyMatch(value -> value <= 0)
                || (!"discord_general".equals(profile) && !"discord_gaming".equals(profile))) {
            throw new IllegalArgumentException("invalid staging receipt scope");
        }
    }

    private static boolean validIdentitySettings(
            byte[] keyMaterial, String destinationBinding, long guildId) {
        return keyMaterial.length == KEY_BYTES && !destinationBinding.isBlank()
                && destinationBinding.length() <= 512 && guildId > 0;
    }

    private Ownership acquireOwnership() throws IOException {
        Path lockPath = directory.resolve(LOCK_NAME);
        validateExistingLockFile(lockPath);
        FileChannel channel = openFile(lockPath, false);
        try {
            FileLock lock = tryExclusiveLock(channel);
            return new Ownership(channel, lock);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    private FileLock tryExclusiveLock(FileChannel channel) throws IOException {
        final FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException busy) {
            throw new IOException("receipt store already owned");
        }
        if (lock == null) {
            throw new IOException("receipt store already owned");
        }
        return lock;
    }

    private void validateExistingLockFile(Path lockPath) throws IOException {
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("invalid receipt store lock path");
            }
            if (Files.getFileStore(directory).supportsFileAttributeView(POSIX_VIEW)
                    && !Files.getPosixFilePermissions(lockPath, LinkOption.NOFOLLOW_LINKS)
                            .equals(PosixFilePermissions.fromString("rw-------"))) {
                throw new IOException("insecure receipt lock permissions");
            }
        }
    }

    Saved save(DiscordChatPublication publication) throws IOException {
        synchronized (this) {
            ensureOpen();
            validatePublication(publication);
            return writeReceipt(publication, availableCapacity());
        }
    }

    private int availableCapacity() throws IOException {
        int totalBytes = 0;
        int count = 0;
        for (Path file : storedFiles()) {
            long length = Files.size(file);
            if (length > MAX_FILE_BYTES) {
                throw new IOException("invalid receipt file size");
            }
            totalBytes += (int) length;
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new IOException("receipt store capacity reached");
            }
            if (file.getFileName().toString().endsWith(EXTENSION)) {
                count++;
            }
        }
        if (count >= MAX_RECORDS) {
            throw new IOException("receipt store capacity reached");
        }
        return MAX_TOTAL_BYTES - totalBytes;
    }

    private Saved writeReceipt(DiscordChatPublication publication, int availableBytes)
            throws IOException {
        long now = clock.millis();
        Sealed data = new Sealed(1, now, publication.guildId(), publication.channelId(),
                publication.discordMessageId(), publication.canonicalMessageId(),
                publication.canonicalPlainText(), profile);
        byte[] encoded = encrypt(data);
        try {
            if (encoded.length > MAX_FILE_BYTES || encoded.length > availableBytes) {
                throw new IOException("receipt store capacity reached");
            }
            String fileName = UUID.randomUUID() + EXTENSION;
            commitReceipt(fileName, encoded);
            return new Saved(fileName, publication, now);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private void commitReceipt(String fileName, byte[] encoded) throws IOException {
        Path temporary = directory.resolve(fileName + ".tmp");
        try {
            try (FileChannel output = openFile(temporary, true)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                output.force(true);
            }
            try {
                Files.move(temporary, directory.resolve(fileName),
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unavailable) {
                throw new IOException("atomic receipt commit unavailable");
            }
            syncDirectory();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    List<Saved> recover() throws IOException {
        synchronized (this) {
            ensureOpen();
            List<Saved> restored = new ArrayList<>();
            for (Path file : recoveryFiles()) {
                Saved candidate = readSaved(file);
                if (clock.millis() - candidate.createdMillis() >= TTL_MILLIS) {
                    Files.delete(file);
                    syncDirectory();
                } else {
                    restored.add(candidate);
                }
            }
            restored.sort(Comparator.comparingLong(Saved::createdMillis));
            return List.copyOf(restored);
        }
    }

    private List<Path> recoveryFiles() throws IOException {
        List<Path> records = new ArrayList<>();
        int totalBytes = 0;
        for (Path file : storedFiles()) {
            if (file.getFileName().toString().endsWith(".tmp")) {
                // A crash-prepared write was never atomically committed.
                Files.delete(file);
                syncDirectory();
                continue;
            }
            long bytes = Files.size(file);
            if (bytes > MAX_FILE_BYTES || bytes < MAGIC.length + NONCE_BYTES + TAG_BITS / 8) {
                throw new IOException("invalid encrypted receipt size");
            }
            totalBytes += (int) bytes;
            if (totalBytes > MAX_TOTAL_BYTES || records.size() >= MAX_RECORDS) {
                throw new IOException("receipt store exceeds recovery limit");
            }
            records.add(file);
        }
        return records;
    }

    private Saved readSaved(Path file) throws IOException {
        byte[] contents = readCiphertext(file);
        Sealed decoded;
        try {
            decoded = decrypt(contents);
        } finally {
            Arrays.fill(contents, (byte) 0);
        }
        if (decoded.version() != 1 || decoded.createdMillis() > clock.millis()
                || decoded.createdMillis() <= 0 || !profile.equals(decoded.channelProfile())) {
            throw new IOException("invalid receipt metadata");
        }
        return validatedSaved(file, decoded);
    }

    private Saved validatedSaved(Path file, Sealed decoded) throws IOException {
        if (decoded.discordMessageId() == null || decoded.canonicalMessageId() == null
                || decoded.canonicalPlainText() == null) {
            throw new IOException("invalid authenticated receipt identity");
        }
        try {
            DiscordChatPublication publication = new DiscordChatPublication(
                    decoded.guildId(), decoded.channelId(), decoded.discordMessageId(),
                    decoded.canonicalMessageId(), decoded.canonicalPlainText());
            validatePublication(publication);
            return new Saved(file.getFileName().toString(), publication,
                    decoded.createdMillis());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid authenticated receipt scope");
        }
    }

    private byte[] readCiphertext(Path file) throws IOException {
        // Open with NOFOLLOW_LINKS, checking size after open to reduce symlink races.
        try (FileChannel input = FileChannel.open(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = input.size();
            if (size > MAX_FILE_BYTES || size < MAGIC.length + NONCE_BYTES + TAG_BITS / 8) {
                throw new IOException("invalid encrypted receipt size");
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) {
                if (input.read(buffer) < 0) {
                    throw new IOException("truncated encrypted receipt");
                }
            }
            return buffer.array();
        }
    }

    void acknowledge(Saved saved) throws IOException {
        synchronized (this) {
            ensureOpen();
            Objects.requireNonNull(saved, "saved");
            if (!saved.fileName().matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mir")) {
                throw new IllegalArgumentException("invalid opaque receipt key");
            }
            Files.deleteIfExists(directory.resolve(saved.fileName()));
            syncDirectory();
        }
    }

    private void validatePublication(DiscordChatPublication receipt) {
        if (receipt == null || receipt.guildId() != guildId
                || !channels.contains(receipt.channelId())) {
            throw new IllegalArgumentException("receipt outside staging public chat scope");
        }
    }

    private void validateDirectory() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new IOException("receipt directory must be pre-provisioned and not a symlink");
        }
        if (Files.getFileStore(directory).supportsFileAttributeView(POSIX_VIEW)
                && !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                        .equals(PRIVATE_DIRECTORY)) {
            throw new IOException("receipt directory must have private permissions");
        }
    }

    private List<Path> storedFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> listing = Files.newDirectoryStream(directory)) {
            for (Path path : listing) {
                String name = path.getFileName().toString();
                if (LOCK_NAME.equals(name)) {
                    continue;
                }
                if (!name.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mir(\\.tmp)?")
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("unexpected or unsafe receipt directory entry");
                }
                files.add(path);
                if (files.size() > MAX_RECORDS * 2) {
                    throw new IOException("receipt store entry count exceeded");
                }
            }
        }
        return files;
    }

    private FileChannel openFile(Path path, boolean exclusiveCreate) throws IOException {
        Set<java.nio.file.OpenOption> options = exclusiveCreate
                ? Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)
                : Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
        if (Files.getFileStore(directory).supportsFileAttributeView(POSIX_VIEW)) {
            return FileChannel.open(path, options, PRIVATE_FILE);
        }
        return FileChannel.open(path, options);
    }

    private byte[] encrypt(Sealed receipt) throws IOException {
        try {
            // A fresh 96-bit nonce comes from the CSPRNG for every encryption.
            // Never reuse a nonce under this journal key. Store it with the
            // ciphertext; check uniqueness over the full 128-record test bound.
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(MAGIC);
            cipher.updateAAD(binding);
            byte[] ciphertext = cipher.doFinal(JSON.writeValueAsBytes(receipt));
            return ByteBuffer.allocate(MAGIC.length + NONCE_BYTES + ciphertext.length)
                    .put(MAGIC).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException | IOException exception) {
            // Do not propagate Jackson exception messages that could contain text.
            throw new IOException("could not seal receipt");
        }
    }

    private Sealed decrypt(byte[] contents) throws IOException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(contents);
            byte[] magic = new byte[MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("unsupported receipt envelope");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(MAGIC);
            cipher.updateAAD(binding);
            return JSON.readValue(cipher.doFinal(ciphertext), Sealed.class);
        } catch (GeneralSecurityException | IOException | RuntimeException exception) {
            // Parsing failures must never reveal decrypted JSON in diagnostics.
            throw new IOException("untrusted or corrupt encrypted receipt");
        }
    }

    private void syncDirectory() throws IOException {
        // On POSIX filesystems, also force the atomic rename or unlink.
        // Windows volume durability still requires platform-specific review.
        if (Files.getFileStore(directory).supportsFileAttributeView(POSIX_VIEW)) {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("receipt store closed");
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (!closed) {
                closed = true;
                try {
                    exclusiveLock.release();
                } finally {
                    lockChannel.close();
                }
            }
        }
    }
}

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
import javax.crypto.spec.IvParameterSpec;
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
    private static final byte[] MAGIC = {'E', 'M', 'R', '2'};
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int MAX_RECORDS = 128;
    private static final int MAX_RECOVERY_ATTEMPTS = 8;
    private static final long SLOW_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_TOTAL_BYTES = 1024 * 1024;
    private static final int MAX_FILE_BYTES = 32 * 1024;
    private static final long TTL_MILLIS = 60 * 60 * 1000L;
    private static final String EXTENSION = ".mir";
    private static final String POSIX_VIEW = "posix";
    private static final String LOCK_NAME = ".mirror.lock";
    private static final String RATE_NAME = ".mirror-rate";
    private static final String RATE_TEMP_NAME = ".mirror-rate.tmp";
    private static final int RATE_VERSION = 2;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final FileAttribute<Set<PosixFilePermission>> PRIVATE_FILE =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    record Saved(String fileName, DiscordChatPublication publication,
                 long createdMillis, int recoveryAttempts, long lastAttemptMillis) {
        @Override
        public String toString() {
            // An accidental log of the wrapper must never print original chat.
            return "Saved[receipt=REDACTED]";
        }
    }

    private record RateState(
            int version, String kind, long guildId, String profile, long lastAttemptMillis) {}

    private record Sealed(
            int version, long createdMillis, long guildId, long channelId,
            String discordMessageId, String canonicalMessageId,
            String canonicalPlainText, String channelProfile,
            int recoveryAttempts, long lastAttemptMillis) {}

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
        this.key = new SecretKeySpec(keyMaterial, "ChaCha20");
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
            return new Ownership(channel, tryExclusiveLock(channel));
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
                publication.canonicalPlainText(), profile, 0, 0L);
        byte[] encoded = encrypt(data);
        try {
            if (encoded.length > MAX_FILE_BYTES || encoded.length > availableBytes) {
                throw new IOException("receipt store capacity reached");
            }
            String fileName = UUID.randomUUID() + EXTENSION;
            commitReceipt(fileName, encoded);
            return new Saved(fileName, publication, now, 0, 0L);
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
            if (bytes > MAX_FILE_BYTES || bytes < MAGIC.length + NONCE_BYTES + TAG_BYTES) {
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
            decoded = decrypt(contents, Sealed.class);
        } finally {
            Arrays.fill(contents, (byte) 0);
        }
        if (decoded.version() != 1 || decoded.createdMillis() > clock.millis()
                || decoded.createdMillis() <= 0 || !profile.equals(decoded.channelProfile())
                || decoded.recoveryAttempts() < 0
                || decoded.recoveryAttempts() > MAX_RECOVERY_ATTEMPTS
                || decoded.lastAttemptMillis() < 0
                || decoded.lastAttemptMillis() > clock.millis()) {
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
                    decoded.createdMillis(), decoded.recoveryAttempts(),
                    decoded.lastAttemptMillis());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid authenticated receipt scope");
        }
    }

    private byte[] readCiphertext(Path file) throws IOException {
        // Open with NOFOLLOW_LINKS, checking size after open to reduce symlink races.
        try (FileChannel input = FileChannel.open(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = input.size();
            if (size > MAX_FILE_BYTES || size < MAGIC.length + NONCE_BYTES + TAG_BYTES) {
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

    /**
     * Persist the next *slow recovery* attempt before any HTTP request can start.
     * Returns empty if the receipt expired or the lifetime attempt budget ended.
     * This is disk-bound and must never run on a JDA callback.
     */
    enum AttemptStatus { READY, DEFERRED, EXHAUSTED }

    record AttemptPreparation(AttemptStatus status, Saved saved) {}

    AttemptPreparation prepareRecoveryAttempt(Saved saved) throws IOException {
        synchronized (this) {
            ensureOpen();
            Path file = receiptPath(saved);
            Saved current = readSaved(file);
            if (!current.equals(saved)) {
                throw new IOException("stale or mismatched receipt recovery state");
            }
            if (recoveryExhausted(current)) {
                Files.delete(file);
                syncDirectory();
                return new AttemptPreparation(AttemptStatus.EXHAUSTED, null);
            }
            if (recoveryDeferred(current) || globalRecoveryDeferred()) {
                return new AttemptPreparation(AttemptStatus.DEFERRED, null);
            }
            persistGlobalAttemptClock();
            return persistNextRecoveryAttempt(file, current);
        }
    }

    private boolean recoveryExhausted(Saved current) {
        return clock.millis() - current.createdMillis() >= TTL_MILLIS
                || current.recoveryAttempts() >= MAX_RECOVERY_ATTEMPTS;
    }

    private boolean globalRecoveryDeferred() throws IOException {
        long last = readGlobalAttemptClock();
        return last > 0 && clock.millis() - last < SLOW_INTERVAL_MILLIS;
    }

    private long readGlobalAttemptClock() throws IOException {
        Path file = directory.resolve(RATE_NAME);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return 0L;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("invalid encrypted recovery rate state");
        }
        if (Files.getFileStore(directory).supportsFileAttributeView(POSIX_VIEW)
                && !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
                        .equals(PosixFilePermissions.fromString("rw-------"))) {
            throw new IOException("insecure recovery rate permissions");
        }
        byte[] data = readCiphertext(file);
        try {
            RateState state = decrypt(data, RateState.class);
            if (state.version() != RATE_VERSION || !"mirror-recovery".equals(state.kind())
                    || state.guildId() != guildId || !profile.equals(state.profile())
                    || state.lastAttemptMillis() <= 0
                    || state.lastAttemptMillis() > clock.millis()) {
                throw new IOException("invalid authenticated recovery rate state");
            }
            return state.lastAttemptMillis();
        } finally {
            Arrays.fill(data, (byte) 0);
        }
    }

    private void persistGlobalAttemptClock() throws IOException {
        byte[] data = encrypt(new RateState(
                RATE_VERSION, "mirror-recovery", guildId, profile, clock.millis()));
        Path committed = directory.resolve(RATE_NAME);
        Path temporary = directory.resolve(RATE_TEMP_NAME);
        try {
            if (data.length > MAX_FILE_BYTES) {
                throw new IOException("invalid recovery rate state size");
            }
            try (FileChannel channel = openFile(temporary, true)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, committed,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            syncDirectory();
        } finally {
            Files.deleteIfExists(temporary);
            Arrays.fill(data, (byte) 0);
        }
    }

    private boolean recoveryDeferred(Saved current) {
        return current.lastAttemptMillis() > 0
                && clock.millis() - current.lastAttemptMillis() < SLOW_INTERVAL_MILLIS;
    }

    private AttemptPreparation persistNextRecoveryAttempt(Path file, Saved current)
            throws IOException {
        int nextAttempt = current.recoveryAttempts() + 1;
        long now = clock.millis();
        Sealed updated = new Sealed(1, current.createdMillis(),
                current.publication().guildId(), current.publication().channelId(),
                current.publication().discordMessageId(),
                current.publication().canonicalMessageId(),
                current.publication().canonicalPlainText(), profile, nextAttempt, now);
        persistAttempt(file, updated);
        Saved counted = new Saved(current.fileName(), current.publication(),
                current.createdMillis(), nextAttempt, now);
        return new AttemptPreparation(AttemptStatus.READY, counted);
    }

    private void persistAttempt(Path committed, Sealed updated) throws IOException {
        byte[] sealed = encrypt(updated);
        Path temporary = directory.resolve(committed.getFileName() + ".tmp");
        try {
            if (sealed.length > MAX_FILE_BYTES) {
                throw new IOException("recovery receipt too large");
            }
            try (FileChannel output = openFile(temporary, true)) {
                ByteBuffer buffer = ByteBuffer.wrap(sealed);
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                output.force(true);
            }
            Files.move(temporary, committed,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            syncDirectory();
        } finally {
            Files.deleteIfExists(temporary);
            Arrays.fill(sealed, (byte) 0);
        }
    }

    private Path receiptPath(Saved saved) throws IOException {
        Objects.requireNonNull(saved, "saved");
        if (!saved.fileName().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mir")) {
            throw new IOException("invalid opaque recovery record");
        }
        return directory.resolve(saved.fileName());
    }

    void acknowledge(Saved saved) throws IOException {
        synchronized (this) {
            ensureOpen();
            Path file = receiptPath(saved);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!readSaved(file).equals(saved)) {
                    throw new IOException("cannot acknowledge stale receipt");
                }
                Files.delete(file);
                syncDirectory();
            }
        }
    }

    boolean accepts(AiMirrorPublicationConfig config) {
        return guildId == config.guildId()
                && channels.equals(config.allowedChannels())
                && profile.equals(config.channelProfile());
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
                if (LOCK_NAME.equals(name) || RATE_NAME.equals(name)) {
                    continue;
                }
                if (RATE_TEMP_NAME.equals(name)) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("invalid recovery rate temporary file");
                    }
                    files.add(path);
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

    private byte[] encrypt(Object receipt) throws IOException {
        try {
            // Each record uses a fresh 96-bit nonce from SecureRandom with a
            // distinct random key supplied per deployment. The 128-record
            // quota bounds the collision probability; nonce is in the envelope.
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(nonce));
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

    private <T> T decrypt(byte[] contents, Class<T> resultType) throws IOException {
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
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(nonce));
            cipher.updateAAD(MAGIC);
            cipher.updateAAD(binding);
            return JSON.readValue(cipher.doFinal(ciphertext), resultType);
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

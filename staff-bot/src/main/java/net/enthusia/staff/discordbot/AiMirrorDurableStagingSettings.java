package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Inert staging-only durable recovery configuration validation.
 *
 * <p>NOT read from or instantiated by StaffBotRuntime. This is intentionally
 * not an activation flag yet: it makes key/volume provisioning testable without
 * changing any running bot mode or allowing production use.</p>
 */
final class AiMirrorDurableStagingSettings {
    static final String ENABLED = "ENTHUSIA_STAFF_BOT_AI_MIRROR_DURABLE_ENABLED";
    static final String JOURNAL_DIRECTORY = "ENTHUSIA_STAFF_BOT_AI_MIRROR_DURABLE_DIRECTORY";
    static final String JOURNAL_KEY_FILE = "ENTHUSIA_STAFF_BOT_AI_MIRROR_DURABLE_KEY_FILE";

    private static final int KEY_BYTES = 32;
    private static final String POSIX_VIEW = "posix";
    private static final Set<java.nio.file.attribute.PosixFilePermission> DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<java.nio.file.attribute.PosixFilePermission> PRIVATE_KEY_READONLY =
            PosixFilePermissions.fromString("r--------");
    private static final Set<java.nio.file.attribute.PosixFilePermission> PRIVATE_KEY_READWRITE =
            PosixFilePermissions.fromString("rw-------");

    private final Path directory;
    private final Path keyFile;
    private final AiMirrorPublicationConfig transport;

    private AiMirrorDurableStagingSettings(
            Path directory, Path keyFile, AiMirrorPublicationConfig transport) {
        this.directory = directory;
        this.keyFile = keyFile;
        this.transport = transport;
    }

    static Optional<AiMirrorDurableStagingSettings> fromEnvironment(
            StaffBotEnvironment environment,
            Optional<AiMirrorPublicationConfig> transport,
            Map<String, String> values) throws IOException {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(values, "values");
        String enabled = values.getOrDefault(ENABLED, "false");
        if ("false".equalsIgnoreCase(enabled)) {
            return Optional.empty();
        }
        if (!"true".equalsIgnoreCase(enabled)
                || environment != StaffBotEnvironment.STAGING
                || transport.isEmpty()) {
            throw new IllegalArgumentException("durable mirror storage is staging-only");
        }
        Path directory = provisionedPath(values, JOURNAL_DIRECTORY);
        Path keyFile = provisionedPath(values, JOURNAL_KEY_FILE);
        verifyProvisioning(directory, keyFile);
        return Optional.of(new AiMirrorDurableStagingSettings(
                directory, keyFile, transport.orElseThrow()));
    }

    boolean boundTo(AiMirrorPublicationConfig current) {
        return transport.equals(current);
    }

    AiMirrorEncryptedOutbox openOutbox(Clock clock) throws IOException {
        Objects.requireNonNull(clock, "clock");
        verifyProvisioning(directory, keyFile);
        byte[] rawKey = readPrivateKey();
        try {
            return new AiMirrorEncryptedOutbox(
                    directory, rawKey, destinationBinding(), transport.guildId(),
                    transport.allowedChannels(), transport.channelProfile(), clock);
        } finally {
            Arrays.fill(rawKey, (byte) 0);
        }
    }

    private byte[] readPrivateKey() throws IOException {
        byte[] rawKey = new byte[KEY_BYTES];
        try (FileChannel channel = FileChannel.open(
                keyFile, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() != KEY_BYTES) {
                throw new IOException("invalid durable mirror key size");
            }
            ByteBuffer bytes = ByteBuffer.wrap(rawKey);
            while (bytes.hasRemaining()) {
                if (channel.read(bytes) < 0) {
                    throw new IOException("truncated durable mirror key");
                }
            }
            if (channel.size() != KEY_BYTES) {
                throw new IOException("durable mirror key size changed during read");
            }
            return rawKey;
        } catch (IOException failure) {
            Arrays.fill(rawKey, (byte) 0);
            // Never include paths or key bytes in operator-facing failures.
            throw new IOException("unable to securely read durable mirror key");
        }
    }

    private String destinationBinding() {
        String channels = transport.allowedChannels().stream()
                .sorted().map(String::valueOf).collect(Collectors.joining(","));
        return "staging:mirror:v1:" + transport.endpoint().toASCIIString()
                + ":" + transport.clientId() + ":" + transport.guildId()
                + ":" + transport.channelProfile() + ":" + channels;
    }

    private static Path provisionedPath(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing durable mirror storage setting");
        }
        Path path = Path.of(value.strip());
        if (!path.isAbsolute() || path.normalize().toString().isBlank()) {
            throw new IllegalArgumentException("durable mirror paths must be absolute");
        }
        return path.normalize();
    }

    private static void verifyProvisioning(Path directory, Path keyFile)
            throws IOException {
        verifyPrivatePaths(directory, keyFile);
        verifyPrivatePermissions(directory, keyFile);
        verifyKeyOwnershipAndSize(directory, keyFile);
    }

    private static void verifyPrivatePaths(Path directory, Path keyFile)
            throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(keyFile) || !Files.isWritable(directory)) {
            throw new IOException("durable mirror private storage is unavailable");
        }
    }

    private static void verifyPrivatePermissions(Path directory, Path keyFile)
            throws IOException {
        if (!Files.getFileStore(directory).supportsFileAttributeView(POSIX_VIEW)
                || !Files.getFileStore(keyFile).supportsFileAttributeView(POSIX_VIEW)) {
            throw new IOException("durable mirror requires verified POSIX permissions");
        }
        if (!Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                .equals(DIRECTORY_PERMISSIONS)) {
            throw new IOException("insecure durable mirror directory permissions");
        }
        var permissions = Files.getPosixFilePermissions(keyFile, LinkOption.NOFOLLOW_LINKS);
        if (!PRIVATE_KEY_READONLY.equals(permissions)
                && !PRIVATE_KEY_READWRITE.equals(permissions)) {
            throw new IOException("insecure durable mirror key permissions");
        }
    }

    private static void verifyKeyOwnershipAndSize(Path directory, Path keyFile)
            throws IOException {
        if (!Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS)
                .equals(Files.getOwner(keyFile, LinkOption.NOFOLLOW_LINKS))
                || Files.size(keyFile) != KEY_BYTES) {
            throw new IOException("durable mirror key ownership or size is invalid");
        }
    }

}

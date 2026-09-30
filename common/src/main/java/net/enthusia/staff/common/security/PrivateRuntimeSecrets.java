package net.enthusia.staff.common.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/** Resolves startup secrets on hosts without configurable process environments. */
public final class PrivateRuntimeSecrets {
    private static final int MAXIMUM_FILE_BYTES = 16_384;

    private PrivateRuntimeSecrets() { }

    public static String required(Path directory, String name, Function<String, String> environment) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(environment, "environment");
        if (name == null || !name.matches("[A-Z][A-Z0-9_]{0,127}")) {
            throw new IllegalArgumentException("Invalid runtime secret name");
        }
        String value = environment.apply(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        Path file = directory.resolve("secrets.properties");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Private runtime secret file is missing");
        }
        Properties secrets = new Properties();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] contents = input.readNBytes(MAXIMUM_FILE_BYTES + 1);
            if (contents.length > MAXIMUM_FILE_BYTES) {
                throw new IllegalStateException("Private runtime secret file is too large");
            }
            secrets.load(new ByteArrayInputStream(contents));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Private runtime secret file cannot be read");
        }
        value = secrets.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required private runtime secret is missing");
        }
        return value;
    }
}

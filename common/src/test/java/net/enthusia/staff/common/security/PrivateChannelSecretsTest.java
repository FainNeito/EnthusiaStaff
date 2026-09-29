package net.enthusia.staff.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateChannelSecretsTest {
    private static final Map<String, String> SOURCES = Map.of(
            "channel.backend-secret", "BACKEND_SECRET",
            "channel.proxy-secret", "PROXY_SECRET",
            "channel.tls-store-password", "TLS_PASSWORD"
    );

    @TempDir
    Path tempDir;

    @Test
    void completeEnvironmentSourceWinsWithoutAFile() {
        Map<String, String> loaded = PrivateChannelSecrets.load(
                tempDir,
                SOURCES,
                variable -> Map.of(
                        "BACKEND_SECRET", "backend-value",
                        "PROXY_SECRET", "proxy-value",
                        "TLS_PASSWORD", "store-value"
                ).get(variable)
        );

        assertEquals("backend-value", loaded.get("channel.backend-secret"));
        assertEquals("proxy-value", loaded.get("channel.proxy-secret"));
        assertEquals("store-value", loaded.get("channel.tls-store-password"));
    }

    @Test
    void privateFileProvidesSecretsWhenEnvironmentIsEmpty() throws IOException {
        Files.writeString(tempDir.resolve(PrivateChannelSecrets.FILE_NAME), String.join("\n",
                "channel.backend-secret=backend-file",
                "channel.proxy-secret=proxy-file",
                "channel.tls-store-password=store-file",
                ""));

        Map<String, String> loaded = PrivateChannelSecrets.load(tempDir, SOURCES, ignored -> null);

        assertEquals("backend-file", loaded.get("channel.backend-secret"));
        assertEquals("proxy-file", loaded.get("channel.proxy-secret"));
        assertEquals("store-file", loaded.get("channel.tls-store-password"));
    }

    @Test
    void partialEnvironmentSourceFailsClosedInsteadOfMixingWithFile() throws IOException {
        Files.writeString(tempDir.resolve(PrivateChannelSecrets.FILE_NAME), String.join("\n",
                "channel.backend-secret=backend-file",
                "channel.proxy-secret=proxy-file",
                "channel.tls-store-password=store-file",
                ""));

        assertThrows(IllegalStateException.class, () -> PrivateChannelSecrets.load(
                tempDir,
                SOURCES,
                variable -> variable.equals("BACKEND_SECRET") ? "backend-environment" : null
        ));
    }

    @Test
    void fileWithUnexpectedEntriesFailsClosed() throws IOException {
        Files.writeString(tempDir.resolve(PrivateChannelSecrets.FILE_NAME), String.join("\n",
                "channel.backend-secret=backend-file",
                "channel.proxy-secret=proxy-file",
                "channel.tls-store-password=store-file",
                "unexpected=value",
                ""));

        assertThrows(
                IllegalStateException.class,
                () -> PrivateChannelSecrets.load(tempDir, SOURCES, ignored -> null)
        );
    }

    @Test
    void missingPrivateFileFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> PrivateChannelSecrets.load(tempDir, SOURCES, ignored -> null)
        );
    }
}

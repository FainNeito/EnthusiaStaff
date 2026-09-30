package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VelocityChannelSecretsWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java"
    );

    @Test
    void initializeChannelUsesResolvedSecretsAndWipesTlsPassword() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        int loader = source.indexOf("VelocityChannelSecrets.load(");
        int environment = source.indexOf("System::getenv", loader);
        int backendKeys = source.indexOf("secrets.backendKeys()", environment);
        int proxyKey = source.indexOf("secrets.proxyKey()", backendKeys);
        int tlsPassword = source.indexOf("secrets.tlsStorePassword()", proxyKey);
        int passwordWipe = source.indexOf("Arrays.fill(tlsStorePassword, '\\0')", tlsPassword);
        int serverCreation = source.indexOf("createChannelServer(", passwordWipe);

        assertOrdered(loader, environment, backendKeys, proxyKey, tlsPassword, passwordWipe, serverCreation);
        assertFalse(source.contains("private static SecretKey secretFromEnvironment("));
        assertFalse(source.contains("private static char[] passwordFromEnvironment("));
    }

    private static void assertOrdered(int... positions) {
        int previous = -1;
        for (int position : positions) {
            assertTrue(position > previous, "runtime channel-secret wiring is missing or out of order");
            previous = position;
        }
    }
}

package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageConfigurationLoaderTest {
    private final MessageConfigurationLoader loader = new MessageConfigurationLoader();

    @Test
    void shippedMessagesLoadWithCurrentSchemaAndDefaults() throws IOException {
        MessageConfigurationSnapshot snapshot = load(shippedYaml());

        assertEquals(MessageConfigurationSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion());
        assertEquals(
                "You do not have permission to view EnthusiaStaff status.",
                snapshot.catalog().text(MessageKey.ESTAFF_STATUS_PERMISSION_DENIED)
        );
        assertEquals(
                "Usage: /estaff <status|reload>",
                snapshot.catalog().render(
                        MessageKey.ESTAFF_USAGE,
                        Map.of("label", "estaff", "operations", "status|reload")
                )
        );
    }

    @Test
    void placeholderValuesAreInsertedLiterally() throws IOException {
        MessageConfigurationSnapshot snapshot = load(shippedYaml());

        assertEquals(
                "Usage: /$1\\staff <status\\reload>",
                snapshot.catalog().render(
                        MessageKey.ESTAFF_USAGE,
                        Map.of("label", "$1\\staff", "operations", "status\\reload")
                )
        );
    }

    @Test
    void missingMessageKeyIsRejected() throws IOException {
        String yaml = shippedYaml().replace(
                "      status: \"You do not have permission to view EnthusiaStaff status.\"\n",
                ""
        );

        ConfigurationValidationException failure = assertThrows(
                ConfigurationValidationException.class,
                () -> load(yaml)
        );

        assertTrue(failure.getMessage().contains("estaff.permissions.status"));
    }

    @Test
    void unknownMessageKeyIsRejected() throws IOException {
        String yaml = shippedYaml().replace(
                "    usage: \"Usage: /{label} <{operations}>\"\n",
                "    usage: \"Usage: /{label} <{operations}>\"\n"
                        + "    unknown: \"unsupported\"\n"
        );

        ConfigurationValidationException failure = assertThrows(
                ConfigurationValidationException.class,
                () -> load(yaml)
        );

        assertTrue(failure.getMessage().contains("unknown key estaff.unknown"));
    }

    @Test
    void wrongPlaceholderSetIsRejected() throws IOException {
        String yaml = shippedYaml().replace(
                "    usage: \"Usage: /{label} <{operations}>\"",
                "    usage: \"Usage: /{label}\""
        );

        ConfigurationValidationException failure = assertThrows(
                ConfigurationValidationException.class,
                () -> load(yaml)
        );

        assertTrue(failure.getMessage().contains("must use placeholders"));
    }

    @Test
    void duplicateYamlKeyIsRejected() throws IOException {
        String yaml = shippedYaml().replace(
                "      status: \"You do not have permission to view EnthusiaStaff status.\"\n",
                "      status: \"You do not have permission to view EnthusiaStaff status.\"\n"
                        + "      status: \"duplicate\"\n"
        );

        assertThrows(ConfigurationValidationException.class, () -> load(yaml));
    }

    private MessageConfigurationSnapshot load(String yaml) {
        return loader.load(new StringReader(yaml), "messages.yml");
    }

    private String shippedYaml() throws IOException {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("messages.yml")) {
            if (input == null) {
                throw new IOException("Missing messages.yml test resource");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
    }
}

package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

class MessageConfigurationLoaderTest {
    private static final String USAGE_LINE =
            "    usage: \"<gray>Usage: <aqua>/{label} {operations}</aqua></gray>\"";

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
                text(snapshot.catalog().component(
                        MessageKey.ESTAFF_USAGE,
                        Map.of("label", "estaff", "operations", "<status|reload>")
                ))
        );
    }

    @Test
    void safeMiniMessageFormattingIsAccepted() throws IOException {
        String yaml = shippedYaml().replace(
                "      validation-passed: \"Configuration validation passed; no runtime state was changed.\"",
                "      validation-passed: \"<gold><bold>Configuration looks good.</bold></gold>\""
        );

        MessageConfigurationSnapshot snapshot = load(yaml);

        assertEquals(
                "Configuration looks good.",
                text(snapshot.catalog().component(MessageKey.ESTAFF_CONFIG_VALIDATION_PASSED))
        );
    }

    @Test
    void interactiveMiniMessageTagsAreRejected() throws IOException {
        String yaml = shippedYaml().replace(
                "      validation-passed: \"Configuration validation passed; no runtime state was changed.\"",
                "      validation-passed: \"<click:run_command:'/op @s'>unsafe</click>\""
        );

        ConfigurationValidationException failure = assertThrows(
                ConfigurationValidationException.class,
                () -> load(yaml)
        );

        assertTrue(failure.getMessage().contains("unsupported MiniMessage tag"));
    }

    @Test
    void placeholderValuesAreInsertedLiterallyWithoutInteractiveEvents() throws IOException {
        MessageConfigurationSnapshot snapshot = load(shippedYaml());
        String label = "$1\\staff<red>";
        String operations = "<click:run_command:'/op @s'>status</click>";

        Component rendered = snapshot.catalog().component(
                MessageKey.ESTAFF_USAGE,
                Map.of("label", label, "operations", operations)
        );

        assertEquals("Usage: /" + label + " " + operations, text(rendered));
        assertFalse(hasClickEvent(rendered));
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
                USAGE_LINE + "\n",
                USAGE_LINE + "\n"
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
                USAGE_LINE,
                "    usage: \"<gray>Usage: <aqua>/{label}</aqua></gray>\""
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

    @Test
    void existingV1CatalogKeepsCustomValuesAndUsesV2Defaults() throws IOException {
        String legacy = v1Yaml().replace(
                "      validation-passed: \"Configuration validation passed; no runtime state was changed.\"",
                "      validation-passed: \"Custom owner wording\""
        );

        MessageConfigurationSnapshot loaded = load(legacy);

        assertEquals(1, loaded.schemaVersion());
        assertEquals("Custom owner wording", loaded.catalog().text(MessageKey.ESTAFF_CONFIG_VALIDATION_PASSED));
        assertEquals(
                "You do not have permission to change vanish or spectator tab visibility.",
                loaded.catalog().text(MessageKey.VANISH_PERMISSION_DENIED)
        );
        assertEquals(
                "Usage: /vanish | /vanish tab <show|hide>",
                text(loaded.catalog().component(
                        MessageKey.VANISH_USAGE,
                        Map.of("label", "vanish", "choices", "<show|hide>")
                ))
        );
    }

    @Test
    void v2CatalogRequiresEveryNewKey() throws IOException {
        String yaml = shippedYaml().replace(
                "    player-only: \"Only a player can change vanish or spectator tab visibility.\"\n",
                ""
        );

        ConfigurationValidationException failure = assertThrows(
                ConfigurationValidationException.class,
                () -> load(yaml)
        );

        assertTrue(failure.getMessage().contains("vanish.player-only"));
    }

    @Test
    void v1CatalogRejectsNewerKeysAndUnknownFutureSchemas() throws IOException {
        ConfigurationValidationException undeclared = assertThrows(
                ConfigurationValidationException.class,
                () -> load(shippedYaml().replace("schema-version: 2", "schema-version: 1"))
        );
        assertTrue(undeclared.getMessage().contains("requires schema-version 2"));

        ConfigurationValidationException future = assertThrows(
                ConfigurationValidationException.class,
                () -> load(shippedYaml().replace("schema-version: 2", "schema-version: 3"))
        );
        assertTrue(future.getMessage().contains("schema-version must be between"));
    }

    @Test
    void legacyCatalogStillRejectsMissingExistingKeys() throws IOException {
        String yaml = v1Yaml().replace(
                "      status: \"You do not have permission to view EnthusiaStaff status.\"\n",
                ""
        );
        assertThrows(ConfigurationValidationException.class, () -> load(yaml));
    }

    @Test
    void vanishPlaceholdersCannotInjectMiniMessageCommands() throws IOException {
        MessageConfigurationSnapshot snapshot = load(shippedYaml());
        String maliciousLabel = "<click:run_command:'/op @s'>vanish</click>";
        Component rendered = snapshot.catalog().component(
                MessageKey.VANISH_USAGE,
                Map.of("label", maliciousLabel, "choices", "<show|hide>")
        );
        assertFalse(hasClickEvent(rendered));
        assertEquals(
                "Usage: /" + maliciousLabel + " | /" + maliciousLabel + " tab <show|hide>",
                text(rendered)
        );
    }

    private String v1Yaml() throws IOException {
        String current = shippedYaml();
        int vanishBlock = current.indexOf("\n  vanish:\n");
        if (vanishBlock < 0) {
            throw new IOException("Shipped messages.yml is missing the v2 vanish family");
        }
        return current.substring(0, vanishBlock)
                .replace("schema-version: 2", "schema-version: 1") + "\n";
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

    private static String text(Component component) {
        StringBuilder rendered = new StringBuilder();
        if (component instanceof TextComponent text) {
            rendered.append(text.content());
        }
        component.children().forEach(child -> rendered.append(text(child)));
        return rendered.toString();
    }

    private static boolean hasClickEvent(Component component) {
        if (component.style().clickEvent() != null) {
            return true;
        }
        return component.children().stream().anyMatch(MessageConfigurationLoaderTest::hasClickEvent);
    }
}

package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DiscordChatBridgeModeTest {
    private static final String BRIDGE_PATH = "discord-chat-bridge";
    private static final String MODE_PATH = BRIDGE_PATH + ".mode";
    private static final String SHADOW_ENABLED_PATH = BRIDGE_PATH + ".shadow-enabled";
    private static final String CUTOVER_ACK_PATH = BRIDGE_PATH + ".authoritative-cutover-ack";

    @Test
    void defaultsDisabledAndPreservesLegacyShadowFallback() {
        YamlConfiguration config = new YamlConfiguration();

        assertEquals(
                DiscordChatBridgeMode.DISABLED,
                DiscordChatBridgeMode.from(config.getConfigurationSection(BRIDGE_PATH))
        );

        config.set(SHADOW_ENABLED_PATH, true);
        assertEquals(
                DiscordChatBridgeMode.SHADOW,
                DiscordChatBridgeMode.from(config.getConfigurationSection(BRIDGE_PATH))
        );
    }

    @Test
    void explicitAuthoritativeModeRequiresCutoverAcknowledgement() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(MODE_PATH, "AUTHORITATIVE");

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordChatBridgeMode.from(config.getConfigurationSection(BRIDGE_PATH))
        );

        config.set(CUTOVER_ACK_PATH, true);
        assertEquals(
                DiscordChatBridgeMode.AUTHORITATIVE,
                DiscordChatBridgeMode.from(config.getConfigurationSection(BRIDGE_PATH))
        );
    }

    @Test
    void explicitModeRejectsConflictingLegacyShadowFlag() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(MODE_PATH, "DISABLED");
        config.set(SHADOW_ENABLED_PATH, true);

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordChatBridgeMode.from(config.getConfigurationSection(BRIDGE_PATH))
        );
    }

    @Test
    void modeNamesAreCaseInsensitiveButUnknownValuesFailClosed() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(MODE_PATH, "shadow");
        assertEquals(
                DiscordChatBridgeMode.SHADOW,
                DiscordChatBridgeMode.from(config.getConfigurationSection(BRIDGE_PATH))
        );

        config.set(MODE_PATH, "production");
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordChatBridgeMode.from(config.getConfigurationSection(BRIDGE_PATH))
        );
    }
}

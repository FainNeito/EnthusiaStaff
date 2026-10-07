package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DiscordChatBridgeModeTest {

    @Test
    void defaultsDisabledAndPreservesLegacyShadowFallback() {
        YamlConfiguration config = new YamlConfiguration();

        assertEquals(
                DiscordChatBridgeMode.DISABLED,
                DiscordChatBridgeMode.from(config.getConfigurationSection("discord-chat-bridge"))
        );

        config.set("discord-chat-bridge.shadow-enabled", true);
        assertEquals(
                DiscordChatBridgeMode.SHADOW,
                DiscordChatBridgeMode.from(
                        config.getConfigurationSection("discord-chat-bridge"))
        );
    }

    @Test
    void explicitAuthoritativeModeRequiresCutoverAcknowledgement() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("discord-chat-bridge.mode", "AUTHORITATIVE");

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordChatBridgeMode.from(
                        config.getConfigurationSection("discord-chat-bridge"))
        );

        config.set("discord-chat-bridge.authoritative-cutover-ack", true);
        assertEquals(
                DiscordChatBridgeMode.AUTHORITATIVE,
                DiscordChatBridgeMode.from(
                        config.getConfigurationSection("discord-chat-bridge"))
        );
    }

    @Test
    void explicitModeRejectsConflictingLegacyShadowFlag() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("discord-chat-bridge.mode", "DISABLED");
        config.set("discord-chat-bridge.shadow-enabled", true);

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordChatBridgeMode.from(
                        config.getConfigurationSection("discord-chat-bridge"))
        );
    }

    @Test
    void modeNamesAreCaseInsensitiveButUnknownValuesFailClosed() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("discord-chat-bridge.mode", "shadow");
        assertEquals(
                DiscordChatBridgeMode.SHADOW,
                DiscordChatBridgeMode.from(
                        config.getConfigurationSection("discord-chat-bridge"))
        );

        config.set("discord-chat-bridge.mode", "production");
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordChatBridgeMode.from(
                        config.getConfigurationSection("discord-chat-bridge"))
        );
    }
}

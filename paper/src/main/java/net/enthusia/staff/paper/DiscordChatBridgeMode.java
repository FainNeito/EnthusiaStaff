package net.enthusia.staff.paper;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/** Explicit Discord chat migration authority state for the Paper-side RoseChat bridge. */
enum DiscordChatBridgeMode {
    DISABLED,
    SHADOW,
    AUTHORITATIVE;

    boolean enabled() {
        return this != DISABLED;
    }

    boolean authoritative() {
        return this == AUTHORITATIVE;
    }

    static DiscordChatBridgeMode from(ConfigurationSection section) {
        if (section == null) {
            return DISABLED;
        }

        DiscordChatBridgeMode selected = configuredMode(section);
        validateAuthoritativeAcknowledgement(section, selected);
        return selected;
    }

    private static DiscordChatBridgeMode configuredMode(ConfigurationSection section) {
        String raw = section.getString("mode");
        boolean legacyShadow = section.getBoolean("shadow-enabled", false);
        if (raw == null || raw.isBlank()) {
            return legacyShadow ? SHADOW : DISABLED;
        }

        DiscordChatBridgeMode selected = parseExplicitMode(raw);
        if (legacyShadow && selected != SHADOW) {
            throw new IllegalArgumentException(
                    "discord-chat-bridge.shadow-enabled conflicts with explicit mode"
            );
        }
        return selected;
    }

    private static DiscordChatBridgeMode parseExplicitMode(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "discord-chat-bridge.mode must be DISABLED, SHADOW, or AUTHORITATIVE",
                    exception
            );
        }
    }

    private static void validateAuthoritativeAcknowledgement(
            ConfigurationSection section,
            DiscordChatBridgeMode selected
    ) {
        if (selected == AUTHORITATIVE
                && !section.getBoolean("authoritative-cutover-ack", false)) {
            throw new IllegalArgumentException(
                    "AUTHORITATIVE chat mode requires authoritative-cutover-ack=true"
            );
        }
    }
}

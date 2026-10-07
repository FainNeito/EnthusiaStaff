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

        String raw = section.getString("mode");
        boolean legacyShadow = section.getBoolean("shadow-enabled", false);
        if (raw == null || raw.isBlank()) {
            return legacyShadow ? SHADOW : DISABLED;
        }

        DiscordChatBridgeMode selected;
        try {
            selected = valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "discord-chat-bridge.mode must be DISABLED, SHADOW, or AUTHORITATIVE",
                    exception
            );
        }

        if (legacyShadow && selected != SHADOW) {
            throw new IllegalArgumentException(
                    "discord-chat-bridge.shadow-enabled conflicts with explicit mode"
            );
        }
        if (selected == AUTHORITATIVE
                && !section.getBoolean("authoritative-cutover-ack", false)) {
            throw new IllegalArgumentException(
                    "AUTHORITATIVE chat mode requires authoritative-cutover-ack=true"
            );
        }
        return selected;
    }
}

package net.enthusia.staff.velocity;

import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;

/** Shared Adventure styling for player-facing Velocity administration output. */
final class VelocityMessageStyle {
    private static final TextColor THEME_GOLD = TextColor.color(0xFFCF1F);
    private static final TextColor THEME_CREAM = TextColor.color(0xFFF6E0);
    private static final TextColor THEME_COPPER = TextColor.color(0xF2B66D);
    private static final String SEPARATOR = " — ";

    private VelocityMessageStyle() {
    }

    enum Tone {
        SUCCESS(NamedTextColor.GREEN),
        WARNING(NamedTextColor.GOLD),
        ERROR(NamedTextColor.RED),
        INFO(THEME_GOLD),
        MUTED(THEME_CREAM);

        private final TextColor color;

        Tone(TextColor color) {
            this.color = color;
        }
    }

    static Component header(String title) {
        return Component.text(title, THEME_GOLD, TextDecoration.BOLD);
    }

    static Component modeHeader(String title, OperationalMode mode) {
        return header(title)
                .append(Component.text(" • ", THEME_COPPER))
                .append(Component.text(displayMode(mode), modeColor(mode), TextDecoration.BOLD));
    }

    static Component section(String title) {
        return Component.text("▸ ", NamedTextColor.GOLD)
                .append(Component.text(title, THEME_GOLD, TextDecoration.BOLD));
    }

    static Component statusRow(String label, String status, String detail, Tone tone) {
        Component row = Component.text("  ", THEME_COPPER)
                .append(Component.text(label, THEME_CREAM))
                .append(Component.text("  ", THEME_COPPER))
                .append(Component.text(status, tone.color));
        if (detail == null || detail.isBlank()) {
            return row;
        }
        return row.append(Component.text(SEPARATOR, THEME_COPPER))
                .append(Component.text(detail, THEME_CREAM));
    }

    static Component success(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    static Component warning(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    static Component command(String value) {
        return Component.text(value, THEME_GOLD);
    }

    static Component id(Object value) {
        return Component.text(String.valueOf(value), THEME_GOLD);
    }

    static Component usage(String usage) {
        int commandStart = usage.indexOf('/');
        if (commandStart < 0) {
            return Component.text(usage, THEME_CREAM);
        }
        return Component.text(usage.substring(0, commandStart), THEME_CREAM)
                .append(command(usage.substring(commandStart)));
    }

    static Component style(Component component) {
        if (!(component instanceof TextComponent text)
                || !component.children().isEmpty()
                || !component.style().isEmpty()) {
            return component;
        }
        return style(text.content());
    }

    static Component style(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.regionMatches(true, 0, "usage:", 0, "usage:".length())) {
            return usage(text);
        }
        Tone tone = VelocityMessageToneClassifier.toneFor(trimmed);
        if (tone == Tone.MUTED) {
            return neutralLine(text);
        }
        return Component.text(text, tone.color);
    }

    static Tone issueTone(String key, String detail, OperationalMode mode) {
        return VelocityMessageToneClassifier.issueTone(key, detail, mode);
    }

    static NamedTextColor modeColor(OperationalMode mode) {
        return switch (mode) {
            case ACTIVE -> NamedTextColor.GREEN;
            case SHADOW_MIGRATION -> NamedTextColor.GOLD;
            case DEGRADED, READ_ONLY_FAILURE -> NamedTextColor.RED;
            default -> NamedTextColor.GOLD;
        };
    }

    static String displayMode(OperationalMode mode) {
        return mode.name().replace('_', ' ');
    }

    static String label(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', ' ').replace('_', ' ');
        if (normalized.isBlank()) {
            return "Runtime";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static Component neutralLine(String text) {
        int separator = text.indexOf(':');
        if (separator <= 0 || separator == text.length() - 1) {
            return Component.text(text, THEME_CREAM);
        }
        return Component.text(text.substring(0, separator), THEME_CREAM)
                .append(Component.text(":", THEME_COPPER))
                .append(Component.text(text.substring(separator + 1), NamedTextColor.WHITE));
    }
}

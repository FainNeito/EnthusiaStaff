package net.enthusia.staff.paper.presentation;

import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;

/** Shared, restrained Adventure styling for player-facing Staff chat output. */
public final class StaffMessageStyle {
    private static final TextColor THEME_GOLD = TextColor.color(0xFFCF1F);
    private static final TextColor THEME_CREAM = TextColor.color(0xFFF6E0);
    private static final TextColor THEME_COPPER = TextColor.color(0xF2B66D);
    private static final String SEPARATOR = " — ";

    private StaffMessageStyle() {
    }

    public enum Tone {
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

    public static Component header(String title) {
        return Component.text(title, THEME_GOLD, TextDecoration.BOLD);
    }

    public static Component header(Component title) {
        return Component.text()
                .color(THEME_GOLD)
                .decorate(TextDecoration.BOLD)
                .append(title)
                .build();
    }

    public static Component modeHeader(String title, OperationalMode mode) {
        return header(title)
                .append(Component.text(" • ", THEME_COPPER))
                .append(Component.text(displayMode(mode), modeColor(mode), TextDecoration.BOLD));
    }

    public static Component modeHeader(Component title, OperationalMode mode) {
        return header(title)
                .append(Component.text(" • ", THEME_COPPER))
                .append(Component.text(displayMode(mode), modeColor(mode), TextDecoration.BOLD));
    }

    public static Component section(String title) {
        return Component.text("▸ ", NamedTextColor.GOLD)
                .append(Component.text(title, THEME_GOLD, TextDecoration.BOLD));
    }

    public static Component statusRow(String label, String status, String detail, Tone tone) {
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

    public static Component statusRow(
            Component label,
            Component status,
            Component detail,
            Tone tone
    ) {
        Component row = Component.text("  ", THEME_COPPER)
                .append(withDefaultColor(label, THEME_CREAM))
                .append(Component.text("  ", THEME_COPPER))
                .append(withDefaultColor(status, tone.color));
        if (detail == null) {
            return row;
        }
        return row.append(Component.text(SEPARATOR, THEME_COPPER))
                .append(withDefaultColor(detail, THEME_CREAM));
    }

    public static Component success(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    public static Component success(Component component) {
        return withDefaultColor(component, NamedTextColor.GREEN);
    }

    public static Component warning(String text) {
        return Component.text(text, NamedTextColor.GOLD);
    }

    public static Component warning(Component component) {
        return withDefaultColor(component, NamedTextColor.GOLD);
    }

    public static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    public static Component error(Component component) {
        return withDefaultColor(component, NamedTextColor.RED);
    }

    public static Component info(String text) {
        return Component.text(text, THEME_CREAM);
    }

    public static Component info(Component component) {
        return withDefaultColor(component, THEME_CREAM);
    }

    public static Component command(String command) {
        return Component.text(command, THEME_GOLD);
    }

    public static Component player(String playerName) {
        return Component.text(playerName, THEME_GOLD);
    }

    public static Component id(Object id) {
        return Component.text(String.valueOf(id), THEME_GOLD);
    }

    public static Component usage(String usage) {
        int commandStart = usage.indexOf('/');
        if (commandStart < 0) {
            return Component.text(usage, THEME_CREAM);
        }
        return Component.text(usage.substring(0, commandStart), THEME_CREAM)
                .append(command(usage.substring(commandStart)));
    }

    /**
     * Adds restrained severity styling to an otherwise plain text component.
     * Deliberately leaves composed or interactive Adventure components untouched.
     */
    public static Component style(Component component) {
        if (!(component instanceof TextComponent text)
                || !component.children().isEmpty()
                || !component.style().isEmpty()) {
            return component;
        }
        return style(text.content());
    }

    public static Component style(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.regionMatches(true, 0, "usage:", 0, "usage:".length())) {
            return usage(text);
        }
        Tone tone = StaffMessageToneClassifier.toneFor(trimmed);
        if (tone == Tone.MUTED) {
            return neutralLine(text);
        }
        return Component.text(text, tone.color);
    }

    public static Tone issueTone(String key, String detail, OperationalMode mode) {
        return StaffMessageToneClassifier.issueTone(key, detail, mode);
    }

    public static NamedTextColor modeColor(OperationalMode mode) {
        return switch (mode) {
            case ACTIVE -> NamedTextColor.GREEN;
            case SHADOW_MIGRATION -> NamedTextColor.GOLD;
            case DEGRADED, READ_ONLY_FAILURE -> NamedTextColor.RED;
            default -> NamedTextColor.GOLD;
        };
    }

    public static String displayMode(OperationalMode mode) {
        return mode.name().replace('_', ' ');
    }

    private static Component withDefaultColor(Component component, TextColor color) {
        return Component.text().color(color).append(component).build();
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

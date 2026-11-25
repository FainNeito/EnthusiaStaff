package net.enthusia.staff.paper.punishment;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.Plugin;

/** Announces only newly committed public punishments, never requests or private case details. */
final class PunishmentPublicAnnouncement {
    private static final TextColor GOLD = TextColor.color(0xFFCF1F);
    private static final TextColor CRIMSON = TextColor.color(0xB91C1C);
    private static final TextColor CREAM = TextColor.color(0xFFF6E0);

    private PunishmentPublicAnnouncement() {
    }

    static void publish(Plugin plugin, CaseVisibility visibility, boolean replayed,
            String playerName, List<SanctionSpec> sanctions, String publicReason) {
        Objects.requireNonNull(plugin, "plugin");
        if (visibility != CaseVisibility.PUBLIC || replayed) {
            return;
        }
        Component notice = message(playerName, sanctions, publicReason);
        // Broadcast on Folia's global region scheduler, not the asynchronous database worker.
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin, () -> plugin.getServer().broadcast(notice)
        );
    }

    static Component message(String playerName, List<SanctionSpec> sanctions, String publicReason) {
        String name = compact(Objects.requireNonNull(playerName, "playerName"), 32);
        String reason = compact(Objects.requireNonNull(publicReason, "publicReason"), 100);
        String action = Objects.requireNonNull(sanctions, "sanctions").stream()
                .map(SanctionSpec::type)
                .map(PunishmentPublicAnnouncement::label)
                .distinct()
                .collect(Collectors.joining(" + "));
        if (action.isBlank()) {
            action = "punishment";
        }
        return Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", GOLD)
                .append(Component.newline())
                .append(Component.text("PUBLIC PUNISHMENT", CRIMSON, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(name, CREAM, TextDecoration.BOLD))
                .append(Component.text(" received ", CREAM))
                .append(Component.text(action, CRIMSON, TextDecoration.BOLD))
                .append(Component.text(".", CREAM))
                .append(Component.newline())
                .append(Component.text("Reason: ", CREAM))
                .append(Component.text(reason, CREAM))
                .append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", GOLD));
    }

    private static String label(SanctionType type) {
        if (type.isBan()) {
            return "a ban";
        }
        return switch (type) {
            case KICK -> "a kick";
            case WARNING -> "a warning";
            case MUTE, PUBLIC_MUTE -> "a mute";
            case REPORT_RESTRICTION -> "a report restriction";
            case REPUTATION_BLACKLIST -> "a reputation restriction";
            case MARKET_BLACKLIST -> "a market restriction";
            default -> "a disciplinary action";
        };
    }

    private static String compact(String value, int maximum) {
        String normalized = value.replaceAll("[\\p{Cntrl}\\n\\r\\t]+", " ").trim();
        if (normalized.isEmpty()) {
            return "Unspecified";
        }
        return normalized.length() <= maximum ? normalized
                : normalized.substring(0, maximum - 1).stripTrailing() + "…";
    }
}

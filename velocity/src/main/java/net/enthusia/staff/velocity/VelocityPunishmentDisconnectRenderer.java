package net.enthusia.staff.velocity;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Compact, player-facing Velocity login denial for active bans. */
final class VelocityPunishmentDisconnectRenderer {
    private static final TextColor GOLD = TextColor.color(0xFFCF1F);
    private static final TextColor CREAM = TextColor.color(0xFFF6E0);
    private static final TextColor CRIMSON = TextColor.color(0xB91C1C);
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter END_TIME = DateTimeFormatter
            .ofPattern("MMM d, uuuu 'at' h:mm:ss a z", Locale.US)
            .withZone(EASTERN);

    private VelocityPunishmentDisconnectRenderer() {
    }

    static Component render(
            ActiveSanction sanction,
            String playerName,
            Instant now,
            String appealsUrl,
            Optional<String> appealCode
    ) {
        Objects.requireNonNull(sanction, "sanction");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(appealsUrl, "appealsUrl");
        Objects.requireNonNull(appealCode, "appealCode");

        Component message = Component.empty()
                .append(Component.text("⚡  Enthusia SMP  ⚡",
                        CRIMSON, TextDecoration.BOLD))
                .append(Component.text("\n"))
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", GOLD))
                .append(Component.text("\n\n"))
                .append(Component.text("❕ ʏᴏᴜ'ʀᴇ ʙᴀɴɴᴇᴅ ꜰʀᴏᴍ ᴛʜᴇ sᴇʀᴠᴇʀ ❕", CREAM))
                .append(Component.text("\n\n"))
                .append(labelValue("👤 ᴜsᴇʀɴᴀᴍᴇ → ", playerName, GOLD))
                .append(Component.text("\n"))
                .append(labelValue("⌚ ᴘᴜɴɪsʜᴇᴅ ᴏɴ → ", END_TIME.format(sanction.issuedAt()), GOLD))
                .append(Component.text("\n"))
                .append(labelValue("✎ ʀᴇᴀsᴏɴ → ", sanction.publicReason(), CREAM))
                .append(Component.text("\n"));

        if (sanction.expiresAt().isPresent()) {
            Instant end = sanction.expiresAt().orElseThrow();
            message = message
                    .append(labelValue("⌛ ᴇxᴘɪʀᴇs ɪɴ → ", remaining(now, end), GOLD))
                    .append(Component.text("\n"))
                    .append(labelValue("📆 ᴇxᴘɪʀʏ ᴅᴀᴛᴇ → ", END_TIME.format(end), GOLD));
        } else {
            message = message.append(labelValue(
                    "❕ ", "ᴛʜɪs ᴘᴜɴɪsʜᴍᴇɴᴛ ɪs ᴘᴇʀᴍᴀɴᴇɴᴛ ❕", CRIMSON));
        }

        message = message
                .append(Component.text("\n\n"))
                .append(labelValue("✉ ᴀᴘᴘᴇᴀʟ → ", appealsUrl, GOLD));

        if (appealCode.isPresent() && !appealCode.orElseThrow().isBlank()) {
            message = message
                    .append(Component.text("\n"))
                    .append(labelValue("Appeal code: ", appealCode.orElseThrow(), CREAM));
        }
        return message;
    }

    /** Deliberately omits seconds: the disconnect screen does not update after it appears. */
    static String remaining(Instant now, Instant end) {
        long seconds = Duration.between(now, end).getSeconds();
        if (seconds <= 0) {
            return "Ending now";
        }
        if (seconds < 60) {
            return "Less than 1 minute";
        }
        long minutes = seconds / 60;
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        long remainderMinutes = minutes % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + remainderMinutes + "m";
        }
        if (minutes >= 60) {
            return hours + "h " + remainderMinutes + "m";
        }
        return minutes + "m";
    }

    private static Component labelValue(String label, String value, TextColor valueColor) {
        return Component.text(label, GOLD)
                .append(Component.text(value, valueColor));
    }
}

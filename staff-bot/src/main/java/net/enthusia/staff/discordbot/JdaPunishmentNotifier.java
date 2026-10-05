package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordDeliveryOutcome;
import net.enthusia.staff.domain.discord.DiscordPunishment;

/** Sends player-facing moderation notifications without changing enforcement state. */
final class JdaPunishmentNotifier {
    static final String APPEAL_CHANNEL =
            "https://discord.com/channels/1410303324745371709/1511217148230373568";
    static final String APPEAL_SITE = "https://enthusia.info/appeal";
    private static final List<String> PRIVATE_EXPLANATION_PREFIXES = List.of(
            "Discord message reference:",
            "External evidence reference:"
    );

    private final DiscordPunishmentConfiguration configuration;

    JdaPunishmentNotifier(DiscordPunishmentConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Discord punishment configuration must be present");
        }
        this.configuration = configuration;
    }

    DiscordDeliveryOutcome notifyApplied(JDA jda, DiscordPunishment punishment) {
        return notify(jda, punishment.targetUserId().value(), appliedMessage(punishment));
    }

    DiscordDeliveryOutcome notifyRemoved(JDA jda, DiscordPunishment punishment) {
        return notify(jda, punishment.targetUserId().value(), removalMessage(punishment));
    }

    DiscordDeliveryOutcome notifyMinecraftBan(JDA jda, MinecraftBanNotification notification) {
        return notify(jda, notification.discordUserId(), minecraftBanMessage(notification));
    }

    private DiscordDeliveryOutcome notify(JDA jda, String userId, String message) {
        try {
            User user = jda.retrieveUserById(userId).complete();
            user.openPrivateChannel().complete()
                    .sendMessage(message)
                    .setAllowedMentions(List.of())
                    .complete();
            return DiscordDeliveryOutcome.DELIVERED;
        } catch (RuntimeException failure) {
            return failureOutcome(failure);
        }
    }

    private String appliedMessage(DiscordPunishment punishment) {
        String action = actionName(punishment.intent().type());
        String duration = duration(punishment);
        StringBuilder message = new StringBuilder("# Punishment Alert\n\n")
                .append("You have been `").append(action).append("` on the Enthusia SMP Discord");
        if (punishment.intent().type() != DiscordConsequenceType.WARNING
                && punishment.intent().type() != DiscordConsequenceType.KICK) {
            message.append(" for `").append(duration).append("`");
        }
        message.append(".\n\n")
                .append("**Reason:** ").append(punishment.intent().publicReason()).append("\n\n");
        playerSafeExplanation(punishment.intent().internalExplanation()).ifPresent(explanation ->
                message.append("**Staff explanation:** ").append(explanation).append("\n\n"));
        appendExpiry(message, punishment.expiresAt());
        appendAppeal(message);
        return message.toString();
    }

    private String removalMessage(DiscordPunishment punishment) {
        StringBuilder message = new StringBuilder("# Punishment Update\n\n")
                .append("Your Enthusia SMP Discord `")
                .append(actionName(punishment.intent().type()))
                .append("` has been ")
                .append(removalAction(punishment))
                .append(".\n\n")
                .append("**Original reason:** ").append(punishment.intent().publicReason()).append("\n\n");
        appendAppeal(message);
        return message.toString();
    }

    static String minecraftBanMessage(MinecraftBanNotification notification) {
        StringBuilder message = new StringBuilder("# Punishment Alert\n\n")
                .append("Your Minecraft account `")
                .append(notification.minecraftName())
                .append("` has been `banned` from the Enthusia SMP");
        if (notification.expiresAt().isPresent()) {
            message.append(" until the time shown below");
        } else {
            message.append(" permanently");
        }
        message.append(".\n\n")
                .append("**Reason:** ").append(notification.publicReason()).append("\n\n");
        appendExpiry(message, notification.expiresAt());
        appendAppeal(message);
        return message.toString();
    }

    private static Optional<String> playerSafeExplanation(String internal) {
        if (internal == null || internal.isBlank()) {
            return Optional.empty();
        }
        String result = internal.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> PRIVATE_EXPLANATION_PREFIXES.stream().noneMatch(line::startsWith))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .strip();
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private static void appendExpiry(StringBuilder message, Optional<Instant> expiresAt) {
        if (expiresAt.isEmpty()) {
            message.append("**Expires:** Permanent\n\n");
            return;
        }
        long epoch = expiresAt.orElseThrow().getEpochSecond();
        message.append("**Expires:** <t:").append(epoch).append(":F> (<t:")
                .append(epoch).append(":R>)\n\n");
    }

    private static void appendAppeal(StringBuilder message) {
        message.append("If you believe this punishment is incorrect, you can appeal in ")
                .append("[the Enthusia appeal channel](").append(APPEAL_CHANNEL).append(")")
                .append(" or at [Enthusia.info/appeal](").append(APPEAL_SITE).append(").");
    }

    private static String removalAction(DiscordPunishment punishment) {
        return switch (punishment.termination()) {
            case END -> "ended";
            case REVOKE -> "revoked";
            case OVERTURN -> "overturned";
            case EXPIRE -> "expired";
            case NONE -> "updated";
        };
    }

    private static String actionName(DiscordConsequenceType type) {
        return switch (type) {
            case WARNING -> "warned";
            case MUTE -> "muted";
            case KICK -> "kicked";
            case BAN -> "banned";
            case CHANNEL_RESTRICTION -> "restricted";
            default -> type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static DiscordDeliveryOutcome failureOutcome(RuntimeException failure) {
        if (failure instanceof ErrorResponseException response) {
            String code = response.getErrorResponse().name();
            return retryableCode(code)
                    ? DiscordDeliveryOutcome.FAILED_RETRYABLE
                    : DiscordDeliveryOutcome.FAILED_TERMINAL;
        }
        return DiscordDeliveryOutcome.FAILED_RETRYABLE;
    }

    private static boolean retryableCode(String code) {
        return code.contains("SERVER") || code.contains("TEMPORAR") || code.contains("RATE_LIMIT");
    }

    private static String duration(DiscordPunishment punishment) {
        return switch (punishment.intent().length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> friendlyDuration(punishment.intent().length().temporary().orElseThrow());
        };
    }

    private static String friendlyDuration(Duration duration) {
        long days = duration.toDays();
        if (days > 0 && duration.equals(Duration.ofDays(days))) {
            return plural(days, "day");
        }
        long hours = duration.toHours();
        if (hours > 0 && duration.equals(Duration.ofHours(hours))) {
            return plural(hours, "hour");
        }
        long minutes = duration.toMinutes();
        if (minutes > 0 && duration.equals(Duration.ofMinutes(minutes))) {
            return plural(minutes, "minute");
        }
        return duration.toString();
    }

    private static String plural(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }

    record MinecraftBanNotification(
            String discordUserId,
            String minecraftName,
            String publicReason,
            Instant issuedAt,
            Optional<Instant> expiresAt
    ) {
        MinecraftBanNotification {
            if (discordUserId == null || discordUserId.isBlank()
                    || minecraftName == null || minecraftName.isBlank()
                    || publicReason == null || publicReason.isBlank()
                    || issuedAt == null || expiresAt == null) {
                throw new IllegalArgumentException("Minecraft ban notification is incomplete");
            }
        }
    }
}

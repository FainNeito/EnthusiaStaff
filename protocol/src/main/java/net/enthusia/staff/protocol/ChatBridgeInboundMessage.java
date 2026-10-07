package net.enthusia.staff.protocol;

import java.util.UUID;
import net.enthusia.staff.common.Checks;

/**
 * Ephemeral Discord-origin chat routed from StaffBot through Velocity to one Paper backend.
 *
 * <p>This wire object is provider-neutral and contains no JDA, DiscordSRV, Bukkit, or RoseChat
 * implementation types. StaffBot authenticates the Discord source and selects the explicit route
 * before creating it; Velocity and Paper revalidate the target identity before admission.</p>
 */
public record ChatBridgeInboundMessage(
        UUID eventId,
        String externalMessageId,
        String canonicalMessageId,
        long createdAtEpochMillis,
        long expiresAtEpochMillis,
        long sourceDiscordChannelId,
        String discordUserId,
        String displayName,
        String targetServerId,
        String logicalChannelId,
        String plainText
) {
    public static final int MAX_PLAIN_TEXT_LENGTH = 2_000;
    public static final long MAX_LIFETIME_MILLIS = 60_000L;

    public ChatBridgeInboundMessage {
        if (eventId == null) {
            throw new IllegalArgumentException("chat bridge inbound event id is required");
        }
        if (sourceDiscordChannelId <= 0) {
            throw new IllegalArgumentException("sourceDiscordChannelId must be positive");
        }
        externalMessageId = safeToken(externalMessageId, "externalMessageId", 128);
        canonicalMessageId = safeToken(canonicalMessageId, "canonicalMessageId", 128);
        discordUserId = safeToken(discordUserId, "discordUserId", 64);
        displayName = safeText(displayName, "displayName", 128, true);
        targetServerId = safeToken(targetServerId, "targetServerId", 64);
        logicalChannelId = safeToken(logicalChannelId, "logicalChannelId", 64);
        plainText = safeText(plainText, "plainText", MAX_PLAIN_TEXT_LENGTH, false);
        if (createdAtEpochMillis < 0
                || expiresAtEpochMillis <= createdAtEpochMillis
                || expiresAtEpochMillis - createdAtEpochMillis > MAX_LIFETIME_MILLIS) {
            throw new IllegalArgumentException("chat bridge inbound message lifetime is invalid");
        }
    }

    public boolean isExpired(long nowEpochMillis) {
        return nowEpochMillis > expiresAtEpochMillis;
    }

    private static String safeToken(String value, String field, int maximumLength) {
        String normalized = Checks.nonBlank(value, field, maximumLength);
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " contains control characters");
        }
        return normalized;
    }

    private static String safeText(
            String value,
            String field,
            int maximumLength,
            boolean trim
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = trim ? value.trim() : value;
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " contains control characters");
        }
        return normalized;
    }
}

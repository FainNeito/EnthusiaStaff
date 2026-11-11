package net.enthusia.staff.protocol;

import java.util.UUID;
import net.enthusia.staff.common.Checks;

/**
 * Ephemeral public Minecraft chat offered to the Discord bridge.
 *
 * <p>This wire object is provider-neutral and contains no JDA or DiscordSRV implementation types.
 * The source backend is explicit because the persistent envelope's server ID changes when Velocity
 * relays the message to another authenticated peer.</p>
 */
public record ChatBridgeOutboundMessage(
        UUID eventId,
        String externalMessageId,
        String canonicalMessageId,
        long createdAtEpochMillis,
        long expiresAtEpochMillis,
        String sourceServerId,
        String logicalChannelId,
        UUID minecraftPlayerId,
        String displayName,
        String plainText
) {
    public static final int MAX_PLAIN_TEXT_LENGTH = 2_000;
    public static final long MAX_LIFETIME_MILLIS = 60_000L;

    public ChatBridgeOutboundMessage {
        if (eventId == null || minecraftPlayerId == null) {
            throw new IllegalArgumentException("chat bridge identifiers are required");
        }
        externalMessageId = safeToken(externalMessageId, "externalMessageId", 128);
        canonicalMessageId = safeToken(canonicalMessageId, "canonicalMessageId", 128);
        sourceServerId = safeToken(sourceServerId, "sourceServerId", 64);
        logicalChannelId = safeToken(logicalChannelId, "logicalChannelId", 64);
        displayName = safeText(displayName, "displayName", 128, true);
        plainText = safeText(plainText, "plainText", MAX_PLAIN_TEXT_LENGTH, false);
        if (createdAtEpochMillis < 0
                || expiresAtEpochMillis <= createdAtEpochMillis
                || expiresAtEpochMillis - createdAtEpochMillis > MAX_LIFETIME_MILLIS) {
            throw new IllegalArgumentException("chat bridge message lifetime is invalid");
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

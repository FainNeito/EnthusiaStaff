package net.enthusia.staff.api.chat;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable request offered to an optional rich chat artifact provider.
 *
 * <p>Providers may use the player UUID and canonical message text to snapshot InteractiveChat
 * item/inventory state, but must not mutate player state or block Minecraft chat.</p>
 */
public record RichChatArtifactRequest(
        UUID eventId,
        UUID minecraftPlayerId,
        String displayName,
        String logicalChannelId,
        String canonicalPlainText,
        long createdAtEpochMillis,
        long expiresAtEpochMillis
) {
    public RichChatArtifactRequest {
        eventId = Objects.requireNonNull(eventId, "eventId");
        minecraftPlayerId = Objects.requireNonNull(minecraftPlayerId, "minecraftPlayerId");
        displayName = requireToken(displayName, "displayName", 128);
        logicalChannelId = requireToken(logicalChannelId, "logicalChannelId", 64);
        canonicalPlainText = requireText(canonicalPlainText, "canonicalPlainText", 2_000);
        if (createdAtEpochMillis < 0 || expiresAtEpochMillis <= createdAtEpochMillis
                || expiresAtEpochMillis - createdAtEpochMillis > 60_000L) {
            throw new IllegalArgumentException("rich chat artifact request lifetime is invalid");
        }
    }

    private static String requireToken(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " contains control characters");
        }
        return value;
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        if (value.chars().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\t')) {
            throw new IllegalArgumentException(field + " contains unsupported control characters");
        }
        return value;
    }
}

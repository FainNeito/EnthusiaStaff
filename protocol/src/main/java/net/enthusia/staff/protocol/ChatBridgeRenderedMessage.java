package net.enthusia.staff.protocol;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.enthusia.staff.common.Checks;

/**
 * Styled Minecraft-origin chat render carried independently from the plain V1 chat wire contract.
 *
 * <p>Adventure JSON preserves exact Minecraft styling semantics for downstream rendering while
 * plain and Markdown forms guarantee readable fallbacks. Binary artifacts are deliberately not
 * embedded in this message.</p>
 */
public record ChatBridgeRenderedMessage(
        UUID eventId,
        String externalMessageId,
        String canonicalMessageId,
        long createdAtEpochMillis,
        long expiresAtEpochMillis,
        String sourceServerId,
        String logicalChannelId,
        UUID minecraftPlayerId,
        String displayName,
        String canonicalPlainText,
        String bodyPlainText,
        String bodyMarkdown,
        String bodyAdventureJson,
        String linePlainText,
        String lineMarkdown,
        String lineAdventureJson
) {
    public static final int MAX_CANONICAL_TEXT_LENGTH = 2_000;
    public static final int MAX_PLAIN_LENGTH = 4_096;
    public static final int MAX_MARKDOWN_LENGTH = 8_192;
    public static final int MAX_ADVENTURE_JSON_BYTES = 65_536;
    public static final long MAX_LIFETIME_MILLIS = 60_000L;

    public ChatBridgeRenderedMessage {
        if (eventId == null || minecraftPlayerId == null) {
            throw new IllegalArgumentException("rendered chat identities are required");
        }
        externalMessageId = safeToken(externalMessageId, "externalMessageId", 128);
        canonicalMessageId = safeToken(canonicalMessageId, "canonicalMessageId", 128);
        sourceServerId = safeToken(sourceServerId, "sourceServerId", 64);
        logicalChannelId = safeToken(logicalChannelId, "logicalChannelId", 64);
        displayName = safeToken(displayName, "displayName", 128);
        canonicalPlainText = safeText(
                canonicalPlainText, "canonicalPlainText", MAX_CANONICAL_TEXT_LENGTH, false);
        bodyPlainText = safeText(bodyPlainText, "bodyPlainText", MAX_PLAIN_LENGTH, false);
        bodyMarkdown = safeText(bodyMarkdown, "bodyMarkdown", MAX_MARKDOWN_LENGTH, false);
        bodyAdventureJson = safeJson(bodyAdventureJson, "bodyAdventureJson");
        linePlainText = safeText(linePlainText, "linePlainText", MAX_PLAIN_LENGTH, false);
        lineMarkdown = safeText(lineMarkdown, "lineMarkdown", MAX_MARKDOWN_LENGTH, false);
        lineAdventureJson = safeJson(lineAdventureJson, "lineAdventureJson");
        if (createdAtEpochMillis < 0
                || expiresAtEpochMillis <= createdAtEpochMillis
                || expiresAtEpochMillis - createdAtEpochMillis > MAX_LIFETIME_MILLIS) {
            throw new IllegalArgumentException("rendered chat lifetime is invalid");
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

    private static String safeText(String value, String field, int maximumLength, boolean trim) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = trim ? value.trim() : value;
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        if (normalized.chars().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\t')) {
            throw new IllegalArgumentException(field + " contains unsupported control characters");
        }
        return normalized;
    }

    private static String safeJson(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_ADVENTURE_JSON_BYTES) {
            throw new IllegalArgumentException(field + " exceeds maximum size");
        }
        return value;
    }
}

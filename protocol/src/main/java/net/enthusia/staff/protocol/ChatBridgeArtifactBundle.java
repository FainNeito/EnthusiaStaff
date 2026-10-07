package net.enthusia.staff.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.common.Checks;

/**
 * Ephemeral artifact bundle sent immediately before the matching styled chat frame.
 */
public record ChatBridgeArtifactBundle(
        UUID eventId,
        long createdAtEpochMillis,
        long expiresAtEpochMillis,
        String sourceServerId,
        String logicalChannelId,
        List<ChatBridgeArtifact> artifacts
) {
    public static final int MAX_ARTIFACTS = 4;
    public static final int MAX_TOTAL_ARTIFACT_BYTES = 458_752;
    public static final long MAX_LIFETIME_MILLIS = 60_000L;

    public ChatBridgeArtifactBundle {
        eventId = Objects.requireNonNull(eventId, "eventId");
        sourceServerId = safeToken(sourceServerId, "sourceServerId", 64);
        logicalChannelId = safeToken(logicalChannelId, "logicalChannelId", 64);
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        if (artifacts.isEmpty() || artifacts.size() > MAX_ARTIFACTS
                || artifacts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("artifact bundle count is invalid");
        }
        long bytes = artifacts.stream().mapToLong(value -> value.data().length).sum();
        if (bytes > MAX_TOTAL_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("artifact bundle exceeds aggregate byte limit");
        }
        if (createdAtEpochMillis < 0
                || expiresAtEpochMillis <= createdAtEpochMillis
                || expiresAtEpochMillis - createdAtEpochMillis > MAX_LIFETIME_MILLIS) {
            throw new IllegalArgumentException("artifact bundle lifetime is invalid");
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
}

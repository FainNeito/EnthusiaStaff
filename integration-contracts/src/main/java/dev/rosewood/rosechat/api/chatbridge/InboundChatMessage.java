package dev.rosewood.rosechat.api.chatbridge;

import java.util.UUID;

/**
 * Compile-time mirror of RoseChat's provider-neutral Discord-origin chat contract.
 */
public record InboundChatMessage(
        UUID eventId,
        String externalMessageId,
        String canonicalMessageId,
        long createdAtEpochMillis,
        long expiresAtEpochMillis,
        String logicalChannelId,
        String displayName,
        String plainText
) {
    public static final int MAX_PLAIN_TEXT_LENGTH = 2_000;
    public static final long MAX_LIFETIME_MILLIS = 60_000L;

    public InboundChatMessage {
        if (eventId == null
                || externalMessageId == null || externalMessageId.isBlank()
                || canonicalMessageId == null || canonicalMessageId.isBlank()
                || logicalChannelId == null || logicalChannelId.isBlank()
                || displayName == null || displayName.isBlank()
                || plainText == null || plainText.isBlank()
                || createdAtEpochMillis < 0
                || expiresAtEpochMillis <= createdAtEpochMillis
                || expiresAtEpochMillis - createdAtEpochMillis > MAX_LIFETIME_MILLIS) {
            throw new IllegalArgumentException("inbound chat message is invalid");
        }
    }

    public boolean isExpired(long nowEpochMillis) {
        return nowEpochMillis > expiresAtEpochMillis;
    }
}

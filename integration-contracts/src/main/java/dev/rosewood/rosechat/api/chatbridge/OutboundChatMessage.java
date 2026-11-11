package dev.rosewood.rosechat.api.chatbridge;

import dev.rosewood.rosechat.api.staff.ChannelClassification;
import java.util.UUID;

/**
 * A policy-approved Minecraft chat message offered to an external chat bridge.
 *
 * @param eventId stable RoseChat event UUID used for duplicate suppression across the bridge
 * @param externalMessageId stable Minecraft-side central moderation idempotency key
 * @param canonicalMessageId stable logical ID shared with any Discord mirror of this message
 * @param createdAtEpochMillis creation time of this export
 * @param expiresAtEpochMillis hard expiry for best-effort delivery
 * @param logicalChannelId RoseChat logical channel id used for explicit routing
 * @param classification privacy classification evaluated before public export
 * @param origin source of the message, used to suppress Discord-to-Minecraft echo
 * @param minecraftPlayerId Minecraft sender UUID when a player identity exists, otherwise {@code null}
 * @param displayName safe presentation name for the sender
 * @param plainText bounded canonical fallback text
 */
public record OutboundChatMessage(
        UUID eventId,
        String externalMessageId,
        String canonicalMessageId,
        long createdAtEpochMillis,
        long expiresAtEpochMillis,
        String logicalChannelId,
        ChannelClassification classification,
        Origin origin,
        UUID minecraftPlayerId,
        String displayName,
        String plainText
) {
    public enum Origin {
        MINECRAFT,
        DISCORD
    }

    public OutboundChatMessage {
        if (eventId == null
                || externalMessageId == null || externalMessageId.isBlank()
                || canonicalMessageId == null || canonicalMessageId.isBlank()
                || logicalChannelId == null || logicalChannelId.isBlank()
                || classification == null
                || origin == null
                || displayName == null || displayName.isBlank()
                || plainText == null
                || createdAtEpochMillis < 0
                || expiresAtEpochMillis < createdAtEpochMillis) {
            throw new IllegalArgumentException("outbound chat message is invalid");
        }
    }

    public boolean isExpired(long nowEpochMillis) {
        return nowEpochMillis > this.expiresAtEpochMillis;
    }
}

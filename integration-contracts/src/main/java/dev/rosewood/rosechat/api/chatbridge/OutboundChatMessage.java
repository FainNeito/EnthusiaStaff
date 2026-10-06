package dev.rosewood.rosechat.api.chatbridge;

import dev.rosewood.rosechat.api.staff.ChannelClassification;
import java.util.UUID;

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
}

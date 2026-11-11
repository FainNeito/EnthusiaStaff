package net.enthusia.staff.discordbot;

import java.util.List;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;

/** Synchronous styled Discord delivery seam used only by the dedicated chat worker. */
@FunctionalInterface
interface DiscordRenderedChatEgress {
    boolean sendRendered(
            long channelId,
            ChatBridgeRenderedMessage message,
            List<ChatBridgeArtifact> artifacts
    );
}

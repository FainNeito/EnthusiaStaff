package net.enthusia.staff.discordbot;

import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;

/** Synchronous styled Discord delivery seam used only by the dedicated chat worker. */
@FunctionalInterface
interface DiscordRenderedChatEgress {
    boolean sendRendered(long channelId, ChatBridgeRenderedMessage message);
}

package net.enthusia.staff.discordbot;

import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;

/** Synchronous, bounded Discord delivery seam used only by the dedicated chat worker. */
@FunctionalInterface
interface DiscordChatEgress {
    boolean send(long channelId, ChatBridgeOutboundMessage message);
}

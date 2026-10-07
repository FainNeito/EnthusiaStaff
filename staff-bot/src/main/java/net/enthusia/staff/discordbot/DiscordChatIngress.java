package net.enthusia.staff.discordbot;

import net.enthusia.staff.protocol.ChatBridgeInboundMessage;

/** Provider-neutral sink for bounded Discord-origin chat admitted by the JDA gateway. */
@FunctionalInterface
interface DiscordChatIngress {
    /**
     * Offers one already guild/channel-validated Discord message to the authenticated transport.
     *
     * @return true when accepted into the bounded best-effort transport path
     */
    boolean offer(ChatBridgeInboundMessage message);
}

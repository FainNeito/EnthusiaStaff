package dev.rosewood.rosechat.api.chatbridge;

/** Compile-time mirror of RoseChat's provider-neutral styled outbound chat render. */
public record RenderedOutboundChatMessage(
        OutboundChatMessage message,
        String bodyPlainText,
        String bodyMarkdown,
        String bodyAdventureJson,
        String linePlainText,
        String lineMarkdown,
        String lineAdventureJson
) {
}

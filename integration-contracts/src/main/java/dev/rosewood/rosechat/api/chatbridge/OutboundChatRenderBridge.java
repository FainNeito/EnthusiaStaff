package dev.rosewood.rosechat.api.chatbridge;

@FunctionalInterface
public interface OutboundChatRenderBridge {
    void publish(RenderedOutboundChatMessage message);
}

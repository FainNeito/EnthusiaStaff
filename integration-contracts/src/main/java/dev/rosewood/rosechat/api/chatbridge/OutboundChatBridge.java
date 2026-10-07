package dev.rosewood.rosechat.api.chatbridge;

@FunctionalInterface
public interface OutboundChatBridge {
    void publish(OutboundChatMessage message);
}

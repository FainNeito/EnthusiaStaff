package dev.rosewood.rosechat.api;

import dev.rosewood.rosechat.api.chatbridge.InboundChatMessage;
import dev.rosewood.rosechat.api.chatbridge.LegacyDiscordChatSuppression;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatBridge;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatBridgeCoordinator;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatRenderBridge;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatRenderBridgeCoordinator;

public final class RoseChatAPI {
    private RoseChatAPI() {
    }

    public static RoseChatAPI getInstance() {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }

    public LegacyDiscordChatSuppression.Registration suppressLegacyDiscordChat() {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }

    public boolean isLegacyDiscordChatSuppressed() {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }

    public OutboundChatBridgeCoordinator.Registration installOutboundChatBridge(
            OutboundChatBridge bridge
    ) {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }

    public OutboundChatRenderBridgeCoordinator.Registration installOutboundChatRenderBridge(
            OutboundChatRenderBridge bridge
    ) {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }

    public boolean dispatchInboundChat(InboundChatMessage message) {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }
}

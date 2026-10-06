package dev.rosewood.rosechat.api.chatbridge;

public final class OutboundChatBridgeCoordinator {
    private OutboundChatBridgeCoordinator() {
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}

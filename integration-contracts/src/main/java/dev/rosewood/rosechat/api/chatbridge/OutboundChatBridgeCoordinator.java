package dev.rosewood.rosechat.api.chatbridge;

public final class OutboundChatBridgeCoordinator {
    public OutboundChatBridgeCoordinator() {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}

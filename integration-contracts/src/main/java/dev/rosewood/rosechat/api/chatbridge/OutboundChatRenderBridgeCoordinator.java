package dev.rosewood.rosechat.api.chatbridge;

public final class OutboundChatRenderBridgeCoordinator {
    private OutboundChatRenderBridgeCoordinator() {
        throw new UnsupportedOperationException("compile-time RoseChat contract only");
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}

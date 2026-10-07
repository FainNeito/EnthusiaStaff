package dev.rosewood.rosechat.api.chatbridge;

@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class LegacyDiscordChatSuppression {
    private LegacyDiscordChatSuppression() {
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}

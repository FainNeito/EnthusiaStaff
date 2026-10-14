package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.protocol.ChatArtifactMessages;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/** Synchronous StaffBot admission boundary for artifact bundles that precede styled chat. */
final class StaffBotChatArtifactIngress implements AutoCloseable {
    private final Clock clock;
    private final Map<StaffBotChatBridgeConfiguration.Route, Long> routes;
    private final StaffBotChatArtifactStore store;
    private final AtomicBoolean accepting = new AtomicBoolean();

    StaffBotChatArtifactIngress(
            StaffBotChatBridgeConfiguration configuration,
            Clock clock,
            StaffBotChatArtifactStore store
    ) {
        this(configuration.routes(), clock, store);
    }

    StaffBotChatArtifactIngress(
            Map<StaffBotChatBridgeConfiguration.Route, Long> routes,
            Clock clock,
            StaffBotChatArtifactStore store
    ) {
        this.routes = Map.copyOf(Objects.requireNonNull(routes, "routes"));
        if (this.routes.isEmpty()) {
            throw new IllegalArgumentException("StaffBot chat artifact routes are required");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.store = Objects.requireNonNull(store, "store");
    }

    boolean accept(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!accepting.get()
                || !StaffBotChatBridgeConfiguration.PROXY_ID.equals(envelope.serverId())
                || !ChatArtifactMessages.ARTIFACTS.equals(envelope.messageType())) {
            return false;
        }
        Optional<ChatBridgeArtifactBundle> decoded = decode(envelope.payloadJson());
        if (decoded.isEmpty()) {
            return false;
        }
        ChatBridgeArtifactBundle bundle = decoded.orElseThrow();
        long now = clock.millis();
        if (!ChatArtifactMessages.transportMessageId(bundle.eventId())
                .equals(envelope.messageId())
                || bundle.isExpired(now)
                || !hasRoute(bundle)) {
            return false;
        }
        return store.put(bundle, now);
    }

    void resume() {
        accepting.set(true);
    }

    void pause() {
        accepting.set(false);
        store.clear();
    }

    private boolean hasRoute(ChatBridgeArtifactBundle bundle) {
        try {
            return routes.containsKey(new StaffBotChatBridgeConfiguration.Route(
                    bundle.sourceServerId(),
                    bundle.logicalChannelId()
            ));
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static Optional<ChatBridgeArtifactBundle> decode(String payloadJson) {
        try {
            return Optional.of(ChatArtifactMessages.decode(payloadJson));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        pause();
    }
}

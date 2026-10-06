package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatBridgeCoordinator;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatMessage;
import dev.rosewood.rosechat.api.staff.ChannelClassification;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.protocol.ChatBridgeMessages;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import net.enthusia.staff.protocol.PersistentChannelClient;

/**
 * Best-effort RoseChat -> authenticated Paper/Velocity chat relay.
 *
 * <p>Socket writes never run on RoseChat's chat path. Work is admitted to a small bounded
 * single-thread queue and is dropped when disconnected, expired, or saturated.</p>
 */
public final class RoseChatOutboundBridgeIntegration implements AutoCloseable {
    private static final int MAXIMUM_QUEUED_MESSAGES = 256;
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    private final String sourceServerId;
    private final Clock clock;
    private final AtomicReference<PersistentChannelClient> channel = new AtomicReference<>();
    private final ThreadPoolExecutor sender;
    private final OutboundChatBridgeCoordinator.Registration registration;

    private RoseChatOutboundBridgeIntegration(String sourceServerId, Clock clock) {
        this.sourceServerId = requireText(sourceServerId, "sourceServerId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sender = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAXIMUM_QUEUED_MESSAGES),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-Chat-Relay");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            this.registration = RoseChatAPI.getInstance().installOutboundChatBridge(this::publish);
        } catch (RuntimeException | LinkageError failure) {
            this.sender.shutdownNow();
            throw failure;
        }
    }

    public static Discovery discoverAndInstall(String sourceServerId, Clock clock) {
        try {
            return new Discovery(
                    Optional.of(new RoseChatOutboundBridgeIntegration(sourceServerId, clock)),
                    ""
            );
        } catch (RuntimeException | LinkageError failure) {
            return Discovery.unavailable(
                    "RoseChat outbound bridge API could not be installed: "
                            + failure.getClass().getSimpleName()
            );
        }
    }

    public void bindChannel(PersistentChannelClient client) {
        channel.set(Objects.requireNonNull(client, "client"));
    }

    public void unbindChannel(PersistentChannelClient client) {
        if (client != null) {
            channel.compareAndSet(client, null);
        }
    }

    private void publish(OutboundChatMessage message) {
        Objects.requireNonNull(message, "message");
        if (message.origin() != OutboundChatMessage.Origin.MINECRAFT
                || message.classification() != ChannelClassification.PUBLIC
                || message.isExpired(clock.millis())) {
            return;
        }

        PersistentChannelClient current = channel.get();
        if (current == null || !current.connected()) {
            return;
        }

        ChatBridgeOutboundMessage wire = new ChatBridgeOutboundMessage(
                message.eventId(),
                message.externalMessageId(),
                message.canonicalMessageId(),
                message.createdAtEpochMillis(),
                message.expiresAtEpochMillis(),
                sourceServerId,
                message.logicalChannelId(),
                message.minecraftPlayerId(),
                message.displayName(),
                message.plainText()
        );
        String payload = ChatBridgeMessages.encodeOutbound(wire);
        try {
            sender.execute(() -> send(current, wire, payload));
        } catch (RejectedExecutionException ignored) {
            // Ephemeral chat is intentionally dropped under pressure.
        }
    }

    private void send(
            PersistentChannelClient expected,
            ChatBridgeOutboundMessage message,
            String payload
    ) {
        if (channel.get() != expected || !expected.connected() || message.isExpired(clock.millis())) {
            return;
        }
        expected.send(message.eventId(), ChatBridgeMessages.OUTBOUND, payload, ACK_TIMEOUT);
    }

    @Override
    public void close() {
        channel.set(null);
        try {
            registration.close();
        } finally {
            sender.shutdownNow();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public record Discovery(Optional<RoseChatOutboundBridgeIntegration> integration, String issue) {
        public Discovery {
            integration = Objects.requireNonNull(integration, "integration");
            issue = Objects.requireNonNull(issue, "issue");
            if (integration.isPresent() == !issue.isEmpty()) {
                throw new IllegalArgumentException("successful RoseChat outbound discovery cannot contain an issue");
            }
        }

        private static Discovery unavailable(String issue) {
            return new Discovery(Optional.empty(), issue);
        }
    }
}

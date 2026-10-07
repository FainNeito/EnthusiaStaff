package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatMessage;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatRenderBridge;
import dev.rosewood.rosechat.api.chatbridge.OutboundChatRenderBridgeCoordinator;
import dev.rosewood.rosechat.api.chatbridge.RenderedOutboundChatMessage;
import dev.rosewood.rosechat.api.staff.ChannelClassification;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import net.enthusia.staff.protocol.ChatRenderMessages;
import net.enthusia.staff.protocol.PersistentChannelClient;

/**
 * Styled RoseChat -> authenticated network relay.
 *
 * <p>Admission failures throw back across the provider-neutral RoseChat render boundary so RoseChat
 * can use its plain V1 fallback. Once admitted, delivery remains best-effort and bounded; socket
 * writes/ACK waits run only on this dedicated worker.</p>
 */
public final class RoseChatOutboundRenderBridgeIntegration implements AutoCloseable {
    static final int MAXIMUM_QUEUED_MESSAGES = 64;
    static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    @FunctionalInterface
    interface ChannelSender {
        boolean send(UUID messageId, String messageType, String payloadJson, Duration timeout);
    }

    private record ChannelBinding(Object identity, java.util.function.BooleanSupplier connected, ChannelSender sender) {
        private ChannelBinding {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(connected, "connected");
            Objects.requireNonNull(sender, "sender");
        }
    }

    private final String sourceServerId;
    private final Clock clock;
    private final AtomicReference<ChannelBinding> channel = new AtomicReference<>();
    private final ThreadPoolExecutor sender;
    private final OutboundChatRenderBridgeCoordinator.Registration registration;

    private RoseChatOutboundRenderBridgeIntegration(String sourceServerId, Clock clock) {
        this(
                sourceServerId,
                clock,
                bridge -> RoseChatAPI.getInstance().installOutboundChatRenderBridge(bridge),
                MAXIMUM_QUEUED_MESSAGES
        );
    }

    RoseChatOutboundRenderBridgeIntegration(
            String sourceServerId,
            Clock clock,
            Function<OutboundChatRenderBridge, OutboundChatRenderBridgeCoordinator.Registration> installer,
            int queueCapacity
    ) {
        this.sourceServerId = requireText(sourceServerId, "sourceServerId");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(installer, "installer");
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("styled chat relay queue capacity must be positive");
        }
        this.sender = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "EnthusiaStaff-Styled-Chat-Relay");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            this.registration = Objects.requireNonNull(installer.apply(this::publish), "registration");
        } catch (RuntimeException | LinkageError failure) {
            this.sender.shutdownNow();
            throw failure;
        }
    }

    public static Discovery discoverAndInstall(String sourceServerId, Clock clock) {
        try {
            RoseChatAPI.class.getMethod(
                    "installOutboundChatRenderBridge",
                    OutboundChatRenderBridge.class
            );
            return new Discovery(
                    Optional.of(new RoseChatOutboundRenderBridgeIntegration(sourceServerId, clock)),
                    ""
            );
        } catch (NoSuchMethodException | RuntimeException | LinkageError failure) {
            return Discovery.unavailable(
                    "RoseChat styled render bridge API could not be installed: "
                            + failure.getClass().getSimpleName()
            );
        }
    }

    public void bindChannel(PersistentChannelClient client) {
        Objects.requireNonNull(client, "client");
        channel.set(new ChannelBinding(
                client,
                client::connected,
                (messageId, messageType, payloadJson, timeout) -> {
                    try {
                        return client.send(messageId, messageType, payloadJson, timeout).join();
                    } catch (RuntimeException failure) {
                        return false;
                    }
                }
        ));
    }

    void bindChannelForTest(
            Object identity,
            java.util.function.BooleanSupplier connected,
            ChannelSender channelSender
    ) {
        channel.set(new ChannelBinding(identity, connected, channelSender));
    }

    public void unbindChannel(PersistentChannelClient client) {
        if (client == null) {
            return;
        }
        ChannelBinding current = channel.get();
        if (current != null && current.identity() == client) {
            channel.compareAndSet(current, null);
        }
    }

    private void publish(RenderedOutboundChatMessage rendered) {
        Objects.requireNonNull(rendered, "rendered");
        OutboundChatMessage message = rendered.message();
        if (message.origin() != OutboundChatMessage.Origin.MINECRAFT
                || message.classification() != ChannelClassification.PUBLIC
                || message.isExpired(clock.millis())) {
            throw new IllegalStateException("styled chat render is no longer eligible");
        }

        ChannelBinding current = channel.get();
        if (current == null || !current.connected().getAsBoolean()) {
            throw new IllegalStateException("styled chat transport is unavailable");
        }

        ChatBridgeRenderedMessage wire = toWire(sourceServerId, rendered);
        String payload = ChatRenderMessages.encode(wire);
        try {
            sender.execute(() -> send(current, wire, payload));
        } catch (RejectedExecutionException failure) {
            throw new IllegalStateException("styled chat relay queue is saturated", failure);
        }
    }

    static ChatBridgeRenderedMessage toWire(
            String sourceServerId,
            RenderedOutboundChatMessage rendered
    ) {
        Objects.requireNonNull(rendered, "rendered");
        OutboundChatMessage message = rendered.message();
        return new ChatBridgeRenderedMessage(
                message.eventId(),
                message.externalMessageId(),
                message.canonicalMessageId(),
                message.createdAtEpochMillis(),
                message.expiresAtEpochMillis(),
                sourceServerId,
                message.logicalChannelId(),
                message.minecraftPlayerId(),
                message.displayName(),
                message.plainText(),
                rendered.bodyPlainText(),
                rendered.bodyMarkdown(),
                rendered.bodyAdventureJson(),
                rendered.linePlainText(),
                rendered.lineMarkdown(),
                rendered.lineAdventureJson()
        );
    }

    private void send(
            ChannelBinding expected,
            ChatBridgeRenderedMessage message,
            String payload
    ) {
        if (channel.get() != expected
                || !expected.connected().getAsBoolean()
                || message.isExpired(clock.millis())) {
            return;
        }
        expected.sender().send(
                message.eventId(),
                ChatRenderMessages.RENDERED,
                payload,
                ACK_TIMEOUT
        );
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

    public record Discovery(Optional<RoseChatOutboundRenderBridgeIntegration> integration, String issue) {
        public Discovery {
            integration = Objects.requireNonNull(integration, "integration");
            issue = Objects.requireNonNull(issue, "issue");
            if (integration.isPresent() == !issue.isEmpty()) {
                throw new IllegalArgumentException(
                        "successful RoseChat styled render discovery cannot contain an issue"
                );
            }
        }

        private static Discovery unavailable(String issue) {
            return new Discovery(Optional.empty(), issue);
        }
    }
}

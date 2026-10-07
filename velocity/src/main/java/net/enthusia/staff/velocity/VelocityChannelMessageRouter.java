package net.enthusia.staff.velocity;

import java.util.Objects;
import java.util.Set;
import net.enthusia.staff.protocol.ChannelMessageHandler;
import net.enthusia.staff.protocol.ProtocolEnvelope;

/**
 * Application-message authorization boundary for authenticated persistent-channel peers.
 *
 * <p>Only configured Paper backend IDs may reach the existing application handlers. Auxiliary
 * peers such as StaffBot remain transport-authenticated but application-denied on ingress.</p>
 */
final class VelocityChannelMessageRouter implements ChannelMessageHandler {
    private final Set<String> paperBackendIds;
    private final VelocityChatBridgeRelay chatRelay;
    private final ChannelMessageHandler paperHandler;

    VelocityChannelMessageRouter(
            Set<String> paperBackendIds,
            VelocityChatBridgeRelay chatRelay,
            ChannelMessageHandler paperHandler
    ) {
        this.paperBackendIds = Set.copyOf(Objects.requireNonNull(paperBackendIds, "paperBackendIds"));
        if (this.paperBackendIds.isEmpty()) {
            throw new IllegalArgumentException("at least one Paper backend is required");
        }
        this.chatRelay = Objects.requireNonNull(chatRelay, "chatRelay");
        this.paperHandler = Objects.requireNonNull(paperHandler, "paperHandler");
    }

    @Override
    public boolean handle(ProtocolEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!paperBackendIds.contains(envelope.serverId())) {
            return false;
        }
        if (chatRelay.handles(envelope)) {
            return chatRelay.accept(envelope);
        }
        return paperHandler.handle(envelope);
    }
}

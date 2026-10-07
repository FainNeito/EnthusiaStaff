package net.enthusia.staff.velocity;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Separates authenticated auxiliary peers from durable Paper backend membership. */
final class VelocityChannelPeerPolicy {
    private static final String STAFF_BOT_PROXY_ID = "VELOCITY";

    private VelocityChannelPeerPolicy() {
    }

    static Set<String> requiredPaperBackends(Set<String> authenticatedPeerIds) {
        Objects.requireNonNull(authenticatedPeerIds, "authenticatedPeerIds");
        Set<String> required = new LinkedHashSet<>(authenticatedPeerIds);
        required.remove(VelocityStaffBotChatSink.PEER_ID);
        if (required.isEmpty()) {
            throw new IllegalArgumentException("at least one Paper backend peer is required");
        }
        return Set.copyOf(required);
    }

    static void validateProxyIdentity(String proxyId, Set<String> authenticatedPeerIds) {
        Objects.requireNonNull(proxyId, "proxyId");
        Objects.requireNonNull(authenticatedPeerIds, "authenticatedPeerIds");
        if (authenticatedPeerIds.contains(VelocityStaffBotChatSink.PEER_ID)
                && !STAFF_BOT_PROXY_ID.equals(proxyId)) {
            throw new IllegalArgumentException("StaffBot peer requires proxy id VELOCITY");
        }
    }
}

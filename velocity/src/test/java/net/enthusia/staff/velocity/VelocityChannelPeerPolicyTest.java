package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VelocityChannelPeerPolicyTest {
    @Test
    void staffBotIsAuthenticatedButExcludedFromDurableBackendQuorum() {
        assertEquals(
                Set.of("HUB", "SMP"),
                VelocityChannelPeerPolicy.requiredPaperBackends(
                        Set.of("HUB", "SMP", VelocityStaffBotChatSink.PEER_ID))
        );
    }

    @Test
    void paperBackendsRemainRequired() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VelocityChannelPeerPolicy.requiredPaperBackends(
                        Set.of(VelocityStaffBotChatSink.PEER_ID))
        );
    }

    @Test
    void staffBotRequiresThePinnedVelocitySignerIdentity() {
        VelocityChannelPeerPolicy.validateProxyIdentity(
                "VELOCITY",
                Set.of("SMP", VelocityStaffBotChatSink.PEER_ID)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> VelocityChannelPeerPolicy.validateProxyIdentity(
                        "PROXY",
                        Set.of("SMP", VelocityStaffBotChatSink.PEER_ID))
        );
    }
}

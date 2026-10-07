package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VelocityChannelPeerPolicyTest {
    private static final String PAPER_SERVER = PAPER_SERVER;

    @Test
    void staffBotIsAuthenticatedButExcludedFromDurableBackendQuorum() {
        assertEquals(
                Set.of("HUB", PAPER_SERVER),
                VelocityChannelPeerPolicy.requiredPaperBackends(
                        Set.of("HUB", PAPER_SERVER, VelocityStaffBotChatSink.PEER_ID))
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
                Set.of(PAPER_SERVER, VelocityStaffBotChatSink.PEER_ID)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> VelocityChannelPeerPolicy.validateProxyIdentity(
                        "PROXY",
                        Set.of(PAPER_SERVER, VelocityStaffBotChatSink.PEER_ID))
        );
    }
}

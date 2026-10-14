package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VelocityChannelPeerPolicyTest {
    private static final String PAPER_SERVER = "SMP";

    @Test
    void staffBotIsAuthenticatedButExcludedFromDurableBackendQuorum() {
        assertEquals(
                Set.of("HUB", PAPER_SERVER),
                VelocityChannelPeerPolicy.requiredPaperBackends(
                        Set.of("HUB", PAPER_SERVER, VelocityStaffBotChatSink.PEER_ID))
        );
    }

    @Test
    void paperBackendProjectionExcludesAuxiliaryPeerWithoutRequiringQuorum() {
        assertEquals(
                Set.of(PAPER_SERVER),
                VelocityChannelPeerPolicy.paperBackendIds(
                        Set.of(PAPER_SERVER, VelocityStaffBotChatSink.PEER_ID))
        );
        assertEquals(
                Set.of(),
                VelocityChannelPeerPolicy.paperBackendIds(
                        Set.of(VelocityStaffBotChatSink.PEER_ID))
        );
    }

    @Test
    void auxiliaryPeerConnectivityDoesNotControlPaperBackendReadiness() {
        Set<String> configured = Set.of(PAPER_SERVER, VelocityStaffBotChatSink.PEER_ID);

        assertTrue(VelocityChannelPeerPolicy.allPaperBackendsConnected(
                configured, Set.of(PAPER_SERVER)));
        assertFalse(VelocityChannelPeerPolicy.allPaperBackendsConnected(
                configured, Set.of(VelocityStaffBotChatSink.PEER_ID)));
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

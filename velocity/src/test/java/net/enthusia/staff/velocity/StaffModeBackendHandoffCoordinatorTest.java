package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.protocol.PersistentChannelServer;
import org.junit.jupiter.api.Test;

class StaffModeBackendHandoffCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRANSFER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SMP = "SMP";
    private static final String HUB = "HUB";

    @Test
    void sourceLifecycleFailureNeverDeniesBackendTravel() {
        FakeTransport transport = new FakeTransport();
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                PersistentChannelServer.DeliveryStatus.REJECTED
        );
        var coordinator = coordinator(transport);

        var decision = coordinator.transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertTrue(decision.allowed());
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                StaffModeBackendHandoffCoordinator.PREPARE_RESUME
        )));
    }

    @Test
    void destinationPrepareFailureNeverDeniesBackendTravel() {
        FakeTransport transport = new FakeTransport();
        transport.statuses.put(
                StaffModeBackendHandoffCoordinator.PREPARE_RESUME,
                PersistentChannelServer.DeliveryStatus.REJECTED
        );

        var decision = coordinator(transport).transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertTrue(decision.allowed());
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.EXIT_REQUEST,
                StaffModeBackendHandoffCoordinator.PREPARE_RESUME
        )));
    }

    @Test
    void unavailableControlChannelStillAllowsBackendTravel() {
        var coordinator = new StaffModeBackendHandoffCoordinator(() -> null, ignored -> Optional.of(session()));

        var decision = coordinator.transfer(PLAYER, session(), SMP, HUB, TRANSFER);

        assertTrue(decision.allowed());
    }

    @Test
    void nonOwnerLifecycleStateDoesNotBlockBackendTravel() {
        FakeTransport transport = new FakeTransport();
        StaffSessionSnapshot recovery = new StaffSessionSnapshot(
                SESSION,
                PLAYER,
                HUB,
                StaffSessionState.RECOVERY_REQUIRED,
                true,
                1,
                "a".repeat(64),
                new byte[]{1},
                Instant.parse("2026-10-01T00:00:00Z"),
                7L
        );

        var decision = coordinator(transport).transfer(PLAYER, recovery, SMP, HUB, TRANSFER);

        assertTrue(decision.allowed());
        assertTrue(transport.types.isEmpty());
    }

    @Test
    void failedConnectionCanStillRequestDestinationCancelAndSourceRollback() {
        FakeTransport transport = new FakeTransport();

        var decision = coordinator(transport).recoverFailedConnection(PLAYER, SMP, HUB, TRANSFER);

        assertFalse(decision.allowed());
        assertTrue(transport.types.equals(List.of(
                StaffModeBackendHandoffCoordinator.CANCEL_RESUME,
                StaffModeBackendHandoffCoordinator.ROLLBACK_RESUME
        )));
    }

    @Test
    void destinationRetryUsesPreparedTransferIdentity() {
        FakeTransport transport = new FakeTransport();

        assertTrue(coordinator(transport).retryDestination(PLAYER, HUB, TRANSFER));
        assertTrue(transport.types.equals(List.of(StaffModeBackendHandoffCoordinator.ROLLBACK_RESUME)));
    }

    private static StaffModeBackendHandoffCoordinator coordinator(FakeTransport transport) {
        return new StaffModeBackendHandoffCoordinator(() -> transport, ignored -> Optional.of(session()));
    }

    private static StaffSessionSnapshot session() {
        return new StaffSessionSnapshot(
                SESSION,
                PLAYER,
                SMP,
                StaffSessionState.ACTIVE,
                true,
                1,
                "a".repeat(64),
                new byte[]{1},
                Instant.parse("2026-10-01T00:00:00Z"),
                7L
        );
    }

    private static final class FakeTransport implements StaffModeBackendHandoffCoordinator.Transport {
        private final Map<String, PersistentChannelServer.DeliveryStatus> statuses = new HashMap<>();
        private final List<String> types = new ArrayList<>();

        @Override
        public Set<String> connectedServers() {
            return Set.of(SMP, HUB);
        }

        @Override
        public PersistentChannelServer.DeliveryStatus send(
                String backendId,
                UUID messageId,
                String messageType,
                String payload,
                Duration timeout
        ) {
            types.add(messageType);
            return statuses.getOrDefault(messageType, PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED);
        }
    }
}

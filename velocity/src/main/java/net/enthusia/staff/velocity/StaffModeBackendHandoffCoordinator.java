package net.enthusia.staff.velocity;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import net.enthusia.staff.protocol.PersistentChannelServer;
import net.enthusia.staff.protocol.TransferSnapshotMessages;

final class StaffModeBackendHandoffCoordinator {
    static final String EXIT_REQUEST = "STAFF_MODE_HANDOFF_EXIT";
    static final String PREPARE_RESUME = "STAFF_MODE_HANDOFF_PREPARE";
    static final String ROLLBACK_RESUME = "STAFF_MODE_HANDOFF_ROLLBACK";
    static final String CANCEL_RESUME = "STAFF_MODE_HANDOFF_CANCEL";
    static final String ABORT_SOURCE = "STAFF_MODE_HANDOFF_ABORT_SOURCE";
    private static final Duration CHANNEL_TIMEOUT = Duration.ofSeconds(9);

    interface Transport {
        Set<String> connectedServers();

        PersistentChannelServer.DeliveryStatus send(
                String backendId, UUID messageId, String messageType, String payload, Duration timeout);
    }

    record Decision(boolean allowed, boolean reconcile, String message) {
        static Decision allow() {
            return new Decision(true, false, "");
        }

        static Decision deny(String message) {
            return new Decision(false, false, message);
        }

        static Decision reconcile(String message) {
            return new Decision(false, true, message);
        }
    }

    private final Supplier<Transport> transport;

    static Transport channelTransport(PersistentChannelServer channel) {
        if (channel == null) {
            return null;
        }
        return new Transport() {
            @Override
            public Set<String> connectedServers() {
                return channel.connectedServers();
            }

            @Override
            public PersistentChannelServer.DeliveryStatus send(
                    String backendId,
                    UUID messageId,
                    String messageType,
                    String payload,
                    Duration timeout
            ) {
                return channel.send(backendId, messageId, messageType, payload, timeout).join();
            }
        };
    }

    StaffModeBackendHandoffCoordinator(
            Supplier<Transport> transport,
            Function<UUID, Optional<StaffSessionSnapshot>> sessions
    ) {
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        java.util.Objects.requireNonNull(sessions, "sessions");
    }

    Decision transfer(
            UUID playerId,
            StaffSessionSnapshot session,
            String current,
            String requested,
            UUID transferId
    ) {
        return transfer(playerId, session, current, requested, transferId, (ignored, ignored2) -> Optional.empty());
    }

    /**
     * Transfers a staff session to another backend (overnight/cross-server).
     *
     * @param snapshotTake takes (and consumes) the lightweight visibility snapshot the source
     *                     backend uploaded for this transfer, if any. The snapshot may be
     *                     forwarded to the destination only after the durable source Staff Mode
     *                     session has closed; it never substitutes for inventory/session ownership.
     */
    Decision transfer(
            UUID playerId,
            StaffSessionSnapshot session,
            String current,
            String requested,
            UUID transferId,
            BiFunction<UUID, UUID, Optional<StaffTransferSnapshot>> snapshotTake
    ) {
        java.util.Objects.requireNonNull(transferId, "transferId");
        java.util.Objects.requireNonNull(snapshotTake, "snapshotTake");

        // Staff Mode must never block ordinary backend travel. The optimized handoff is
        // best-effort: source quit/detach and destination database recovery are authoritative
        // fallbacks when the control channel or persistence is slow.
        if (!StaffSessionTransferPolicy.activeHandoffAllowed(
                session.serverId(), session.state(), current, requested)) {
            return Decision.allow();
        }
        Transport channel = transport.get();
        if (!ready(channel, current, requested)) {
            return Decision.allow();
        }

        channel.send(
                current,
                UUID.randomUUID(),
                EXIT_REQUEST,
                exitPayload(playerId, session, transferId),
                CHANNEL_TIMEOUT
        );
        Optional<StaffTransferSnapshot> snapshot = snapshotTake.apply(playerId, transferId);
        prepareDestination(channel, playerId, transferId, requested, snapshot);
        return Decision.allow();
    }

    Decision recoverFailedConnection(
            UUID playerId,
            String source,
            String destination,
            UUID transferId
    ) {
        Transport channel = transport.get();
        if (channel == null) {
            return Decision.deny("Staff Mode rollback could not start because the backend channel is offline.");
        }
        cancelDestination(channel, playerId, transferId, destination);
        return rollbackSource(channel, playerId, transferId, source);
    }

    boolean retryDestination(UUID playerId, String destination, UUID transferId) {
        Transport channel = transport.get();
        return channel != null
                && channel.send(destination, UUID.randomUUID(), ROLLBACK_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT)
                == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
    }

    void cancelPreparedDestination(UUID playerId, String destination, UUID transferId) {
        Transport channel = transport.get();
        if (channel != null) {
            cancelDestination(channel, playerId, transferId, destination);
        }
    }

    boolean abortSourceHandoff(UUID playerId, String source, UUID transferId) {
        Transport channel = transport.get();
        return channel != null && abortSource(channel, playerId, transferId, source)
                == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
    }

    private boolean prepareDestination(
            Transport channel,
            UUID playerId,
            UUID transferId,
            String requested,
            Optional<StaffTransferSnapshot> snapshot
    ) {
        return channel.send(requested, UUID.randomUUID(), PREPARE_RESUME,
                resumePayload(playerId, transferId, snapshot), CHANNEL_TIMEOUT)
                == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED;
    }

    private PersistentChannelServer.DeliveryStatus abortSource(
            Transport channel,
            UUID playerId,
            UUID transferId,
            String source
    ) {
        return channel.send(source, UUID.randomUUID(), ABORT_SOURCE,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT);
    }

    private void cancelDestination(Transport channel, UUID playerId, UUID transferId, String destination) {
        channel.send(destination, UUID.randomUUID(), CANCEL_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT);
    }

    private Decision rollbackSource(Transport channel, UUID playerId, UUID transferId, String current) {
        var status = channel.send(current, UUID.randomUUID(), ROLLBACK_RESUME,
                resumePayload(playerId, transferId), CHANNEL_TIMEOUT);
        if (status == PersistentChannelServer.DeliveryStatus.ACKNOWLEDGED) {
            return Decision.deny("The destination was not ready; Staff Mode rollback was accepted on the current backend.");
        }
        return Decision.deny(
                "The destination was not ready. Your original state is safe, but Staff Mode could not be resumed automatically.");
    }

    private static boolean ready(Transport channel, String current, String requested) {
        if (channel == null) {
            return false;
        }
        Set<String> connected = channel.connectedServers();
        return containsIgnoreCase(connected, current) && containsIgnoreCase(connected, requested);
    }

    private static boolean containsIgnoreCase(Set<String> values, String expected) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private static String exitPayload(
            UUID playerId, StaffSessionSnapshot session, UUID transferId
    ) {
        return "{\"playerId\":\"" + playerId + "\",\"sessionId\":\"" + session.sessionId()
                + "\",\"revision\":" + session.revision() + ",\"transferId\":\"" + transferId + "\"}";
    }

    private static String resumePayload(UUID playerId, UUID transferId) {
        return resumePayload(playerId, transferId, Optional.empty());
    }

    private static String resumePayload(
            UUID playerId,
            UUID transferId,
            Optional<StaffTransferSnapshot> snapshot
    ) {
        String payload = "{\"playerId\":\"" + playerId + "\",\"transferId\":\"" + transferId + "\"";
        if (snapshot.isPresent()) {
            payload += ",\"" + TransferSnapshotMessages.PAYLOAD_FIELD + "\":"
                    + TransferSnapshotMessages.encode(snapshot.orElseThrow());
        }
        return payload + "}";
    }
}

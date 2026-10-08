package net.enthusia.staff.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import net.enthusia.staff.protocol.TransferSnapshotMessages;

final class PaperStaffModeHandoffHandler {
    static final String EXIT_REQUEST = "STAFF_MODE_HANDOFF_EXIT";
    static final String PREPARE_RESUME = "STAFF_MODE_HANDOFF_PREPARE";
    static final String ROLLBACK_RESUME = "STAFF_MODE_HANDOFF_ROLLBACK";
    static final String CANCEL_RESUME = "STAFF_MODE_HANDOFF_CANCEL";
    static final String ABORT_SOURCE = "STAFF_MODE_HANDOFF_ABORT_SOURCE";
    static final String READY = "STAFF_MODE_READY";
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration HANDOFF_RESTORE_WAIT = Duration.ofSeconds(2);
    private static final String PLAYER_ID_FIELD = "playerId";
    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String TRANSFER_ID_FIELD = "transferId";

    /**
     * Cross-server transfer snapshot hook (overnight/cross-server). Implemented by
     * {@code StaffTransferSnapshotCoordinator}; may be {@code null} when transfer snapshots
     * are unavailable.
     */
    interface TransferSnapshotHook {
        /** Source side: capture the in-memory snapshot and upload it to the proxy. Never blocks. */
        void captureAndUpload(UUID playerId, UUID transferId);

        /** Destination side: stash a snapshot received inside a prepare payload. */
        void stashReceived(StaffTransferSnapshot snapshot);
    }

    interface Operations {
        CompletableFuture<Boolean> close(UUID playerId, UUID sessionId, long revision, UUID transferId);

        boolean abortSource(UUID playerId, UUID transferId);

        boolean prepare(UUID playerId, UUID transferId);

        boolean cancel(UUID playerId, UUID transferId);

        CompletableFuture<Boolean> rollback(UUID playerId, UUID transferId);
    }

    private final ObjectMapper json;
    private final Operations operations;
    private final TransferSnapshotHook transferSnapshots;
    private final Logger logger;

    PaperStaffModeHandoffHandler(ObjectMapper json, Operations operations) {
        this(json, operations, null, Logger.getLogger(PaperStaffModeHandoffHandler.class.getName()));
    }

    PaperStaffModeHandoffHandler(
            ObjectMapper json,
            Operations operations,
            TransferSnapshotHook transferSnapshots,
            Logger logger
    ) {
        this.json = java.util.Objects.requireNonNull(json, "json");
        this.operations = java.util.Objects.requireNonNull(operations, "operations");
        this.transferSnapshots = transferSnapshots;
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    static PaperStaffModeHandoffHandler forManager(ObjectMapper json, StaffModeManager manager) {
        java.util.Objects.requireNonNull(manager, "manager");
        return forManager(json, manager, null, Logger.getLogger(PaperStaffModeHandoffHandler.class.getName()));
    }

    static PaperStaffModeHandoffHandler forManager(
            ObjectMapper json,
            StaffModeManager manager,
            TransferSnapshotHook transferSnapshots,
            Logger logger
    ) {
        java.util.Objects.requireNonNull(manager, "manager");
        java.util.Objects.requireNonNull(logger, "logger");
        return new PaperStaffModeHandoffHandler(json, new Operations() {
            @Override
            public CompletableFuture<Boolean> close(
                    UUID playerId,
                    UUID sessionId,
                    long revision,
                    UUID transferId
            ) {
                return manager.closeForBackendHandoff(playerId, sessionId, revision, transferId);
            }

            @Override
            public boolean abortSource(UUID playerId, UUID transferId) {
                return manager.abortBackendHandoffSource(playerId, transferId);
            }

            @Override
            public boolean prepare(UUID playerId, UUID transferId) {
                return manager.prepareBackendHandoffResume(playerId, transferId);
            }

            @Override
            public boolean cancel(UUID playerId, UUID transferId) {
                return manager.cancelBackendHandoffResume(playerId, transferId);
            }

            @Override
            public CompletableFuture<Boolean> rollback(UUID playerId, UUID transferId) {
                return manager.rollbackBackendHandoff(playerId, transferId);
            }
        }, transferSnapshots, logger);
    }

    boolean handles(ProtocolEnvelope envelope) {
        return switch (envelope.messageType()) {
            case EXIT_REQUEST, PREPARE_RESUME, ROLLBACK_RESUME, CANCEL_RESUME, ABORT_SOURCE -> true;
            default -> false;
        };
    }

    boolean handle(ProtocolEnvelope envelope) {
        try {
            JsonNode payload = json.readTree(envelope.payloadJson());
            return switch (envelope.messageType()) {
                case EXIT_REQUEST -> handleExitRequest(payload);
                case ABORT_SOURCE -> operations.abortSource(
                        uuid(payload, PLAYER_ID_FIELD), uuid(payload, TRANSFER_ID_FIELD));
                case PREPARE_RESUME -> handlePrepareResume(payload);
                case CANCEL_RESUME -> operations.cancel(uuid(payload, PLAYER_ID_FIELD), uuid(payload, TRANSFER_ID_FIELD));
                case ROLLBACK_RESUME -> await(operations.rollback(
                        uuid(payload, PLAYER_ID_FIELD), uuid(payload, TRANSFER_ID_FIELD)));
                default -> false;
            };
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean handleExitRequest(JsonNode payload) {
        UUID playerId = uuid(payload, PLAYER_ID_FIELD);
        UUID transferId = uuid(payload, TRANSFER_ID_FIELD);
        if (transferSnapshots != null) {
            // Capture lightweight visibility metadata early so the destination may present
            // vanish immediately. It never substitutes for backend-local saved-state ownership;
            // source disconnect/detach and destination capture/rebind remain authoritative.
            try {
                transferSnapshots.captureAndUpload(playerId, transferId);
            } catch (RuntimeException exception) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "Transfer snapshot capture threw for " + playerId + "; continuing with the close",
                            exception);
                }
            }
        }
        return awaitHandoffRestore(operations.close(
                playerId,
                uuid(payload, SESSION_ID_FIELD),
                payload.path("revision").asLong(-1L),
                transferId
        ), playerId);
    }

    private boolean handlePrepareResume(JsonNode payload) {
        UUID playerId = uuid(payload, PLAYER_ID_FIELD);
        UUID transferId = uuid(payload, TRANSFER_ID_FIELD);
        boolean prepared = operations.prepare(playerId, transferId);
        if (prepared && transferSnapshots != null && payload.has(TransferSnapshotMessages.PAYLOAD_FIELD)) {
            try {
                StaffTransferSnapshot snapshot = TransferSnapshotMessages.decodeNode(
                        payload.path(TransferSnapshotMessages.PAYLOAD_FIELD));
                if (snapshot != null) {
                    transferSnapshots.stashReceived(snapshot);
                }
            } catch (RuntimeException exception) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "Ignoring invalid transfer snapshot in prepare payload for " + playerId, exception);
                }
            }
        }
        return prepared;
    }

    static String readyPayload(UUID playerId, UUID sessionId) {
        return "{\"playerId\":\"" + playerId + "\",\"sessionId\":\"" + sessionId + "\"}";
    }

    private static UUID uuid(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            throw new IllegalArgumentException("missing handoff field");
        }
        return UUID.fromString(payload.path(field).asText());
    }

    private static boolean await(CompletableFuture<Boolean> future) {
        try {
            return future.get(OPERATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(false);
            return false;
        } catch (java.util.concurrent.ExecutionException exception) {
            return false;
        }
    }

    /**
     * Gives the source a short head start to restore/detach its backend-local state, but never
     * turns a slow persistence path into a denied server transfer. PlayerQuitEvent provides a
     * second restore/detach path if the switch wins the race.
     */
    private boolean awaitHandoffRestore(CompletableFuture<Boolean> future, UUID playerId) {
        try {
            return future.get(HANDOFF_RESTORE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return true;
        } catch (java.util.concurrent.TimeoutException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Staff Mode handoff restore is still running for " + playerId
                        + "; allowing the backend switch and relying on disconnect/destination reconciliation");
            }
            return true;
        } catch (java.util.concurrent.ExecutionException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                        "Staff Mode handoff restore failed before transfer for " + playerId
                                + "; allowing the switch and relying on lifecycle reconciliation",
                        exception.getCause());
            }
            return true;
        }
    }

}

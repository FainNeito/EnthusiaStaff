package net.enthusia.staff.velocity;

import net.enthusia.staff.domain.staff.StaffSessionState;

final class StaffSessionTransferPolicy {
    private StaffSessionTransferPolicy() {
    }

    static boolean recoveryReturnAllowed(
            String ownerServerId,
            StaffSessionState state,
            String currentServerId,
            String requestedServerId
    ) {
        if (!validEndpoints(ownerServerId, currentServerId, requestedServerId)) {
            return false;
        }
        return restorableState(state)
                && !ownerServerId.equalsIgnoreCase(currentServerId)
                && ownerServerId.equalsIgnoreCase(requestedServerId);
    }

    private static boolean validEndpoints(String ownerServerId, String currentServerId, String requestedServerId) {
        return ownerServerId != null && !ownerServerId.isBlank()
                && currentServerId != null && requestedServerId != null;
    }

    private static boolean restorableState(StaffSessionState state) {
        return state == StaffSessionState.ACTIVE
                || state == StaffSessionState.RECOVERY_REQUIRED
                || state == StaffSessionState.EXITING;
    }
}

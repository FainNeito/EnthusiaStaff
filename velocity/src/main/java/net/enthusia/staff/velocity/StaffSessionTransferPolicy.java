package net.enthusia.staff.velocity;

import net.enthusia.staff.domain.staff.StaffSessionState;

/** Allows a stranded session to reach the only backend that can restore its snapshot. */
final class StaffSessionTransferPolicy {
    private StaffSessionTransferPolicy() {}

    static boolean recoveryReturnAllowed(
            String owner, StaffSessionState state, String current, String requested
    ) {
        if (owner == null || owner.isBlank() || current == null || requested == null) {
            return false;
        }
        return (state == StaffSessionState.ACTIVE || state == StaffSessionState.RECOVERY_REQUIRED || state == StaffSessionState.EXITING)
                && !owner.equalsIgnoreCase(current) && owner.equalsIgnoreCase(requested);
    }
}

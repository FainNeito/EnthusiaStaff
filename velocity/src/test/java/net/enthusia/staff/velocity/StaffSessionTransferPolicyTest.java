package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

class StaffSessionTransferPolicyTest {
    @Test
    void strandedRecoveryMayReturnOnlyToItsOriginalBackend() {
        for (var state : new StaffSessionState[]{StaffSessionState.RECOVERY_REQUIRED, StaffSessionState.EXITING}) {
            assertTrue(StaffSessionTransferPolicy.recoveryReturnAllowed("SMP", state, "HUB", "smp"));
            assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("SMP", state, "HUB", "TEST"));
            assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("SMP", state, "SMP", "HUB"));
            assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("SMP", state, "SMP", "SMP"));
        }
    }

    @Test
    void activeOrIncompleteSessionsAndMissingOwnershipRemainBlocked() {
        for (var state : new StaffSessionState[]{StaffSessionState.ACTIVE, StaffSessionState.ENTERING, StaffSessionState.CLOSED}) {
            assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("SMP", state, "HUB", "SMP"));
        }
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed(null, StaffSessionState.RECOVERY_REQUIRED, "HUB", "SMP"));
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("", StaffSessionState.RECOVERY_REQUIRED, "HUB", "SMP"));
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("SMP", StaffSessionState.RECOVERY_REQUIRED, null, "SMP"));
        assertFalse(StaffSessionTransferPolicy.recoveryReturnAllowed("SMP", StaffSessionState.RECOVERY_REQUIRED, "HUB", null));
    }
}

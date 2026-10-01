package net.enthusia.staff.paper.staff;

/** Decides how an exceptional death event should be contained for the current Staff Mode lifecycle. */
final class StaffModeDeathPolicy {
    enum Action {
        IGNORE,
        CONTAIN,
        CONTAIN_AND_EXIT
    }

    private StaffModeDeathPolicy() {
    }

    static Action decide(boolean activeSession, boolean usableAuthority) {
        if (!activeSession) {
            return Action.IGNORE;
        }
        return usableAuthority ? Action.CONTAIN_AND_EXIT : Action.CONTAIN;
    }
}

package net.enthusia.staff.paper.staff;

import java.util.Objects;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.event.block.Action;

/** Rank-scoped rules for the non-participating Helper staff-mode profile. */
final class HelperObserverPolicy {
    private HelperObserverPolicy() {
    }

    static boolean applies(boolean activeStaffMode, StaffRank rank) {
        // Fail-closed: if Staff Mode is active but the rank is unresolvable
        // (transition window), apply protections until reconciliation finishes.
        // Matches StaffModeWorldInteractionPolicy fail-closed on null tier.
        return activeStaffMode && (rank == null || rank == StaffRank.HELPER);
    }

    static boolean blocksAirItemUse(
            boolean activeStaffMode,
            StaffRank rank,
            Action action,
            boolean hasOrdinaryItem
    ) {
        Objects.requireNonNull(action, "action");
        return applies(activeStaffMode, rank)
                && hasOrdinaryItem
                && action == Action.RIGHT_CLICK_AIR;
    }

    static boolean blocksProjectileCollision(
            boolean activeStaffMode,
            StaffRank rank,
            boolean hitEntity
    ) {
        return applies(activeStaffMode, rank) && hitEntity;
    }

    static int experienceAmount(boolean activeStaffMode, StaffRank rank, int proposedAmount) {
        return applies(activeStaffMode, rank) ? 0 : proposedAmount;
    }
}

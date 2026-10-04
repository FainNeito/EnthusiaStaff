package net.enthusia.staff.paper.visibility;

import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.staff.StaffModeAccessPolicy;
import org.bukkit.GameMode;

/** Vanish changes visibility; real game-mode selection remains bounded by rank authority. */
final class VanishGameModePolicy {
    private VanishGameModePolicy() {
    }

    static GameMode defaultMode(StaffRank rank) {
        return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER ? GameMode.CREATIVE : GameMode.SPECTATOR;
    }

    static boolean allows(StaffRank rank, GameMode mode) {
        if (rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER) {
            return mode == GameMode.SURVIVAL || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
        }
        return rank != null && rank != StaffRank.SYSTEM && mode == GameMode.SPECTATOR;
    }

    static GameMode reconcile(StaffRank rank, GameMode selected) {
        return allows(rank, selected) ? selected : defaultMode(rank);
    }

    static GameMode defaultMode(StaffRank rank, boolean onDuty) {
        return onDuty ? StaffModeAccessPolicy.initialGameMode(rank) : defaultMode(rank);
    }

    static boolean allows(StaffRank rank, GameMode mode, boolean onDuty) {
        return onDuty ? StaffModeAccessPolicy.allowsGameMode(rank, mode) : allows(rank, mode);
    }

    static GameMode reconcile(StaffRank rank, GameMode selected, boolean onDuty) {
        return allows(rank, selected, onDuty) ? selected : defaultMode(rank, onDuty);
    }
}

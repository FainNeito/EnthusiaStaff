package net.enthusia.staff.paper.integration;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;

final class PolarSpectatorPhasePolicy {
    private static final String PHASE = "PHASE";

    private PolarSpectatorPhasePolicy() {
    }

    static boolean eligibleSpectator(GameMode gameMode, StaffRank rank) {
        return gameMode == GameMode.SPECTATOR
                && rank != null
                && rank != StaffRank.SYSTEM;
    }

    static boolean shouldCancelMitigation(String checkType, boolean eligibleSpectator) {
        return eligibleSpectator && PHASE.equals(checkType);
    }
}

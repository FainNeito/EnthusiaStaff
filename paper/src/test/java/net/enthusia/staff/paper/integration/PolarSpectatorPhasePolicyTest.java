package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class PolarSpectatorPhasePolicyTest {
    @Test
    void explicitStaffSpectatorIsEligible() {
        assertTrue(PolarSpectatorPhasePolicy.eligibleSpectator(GameMode.SPECTATOR, StaffRank.HELPER));
        assertTrue(PolarSpectatorPhasePolicy.eligibleSpectator(GameMode.SPECTATOR, StaffRank.FOUNDER));
    }

    @Test
    void nonSpectatorOrNonPlayerAuthorityIsNotEligible() {
        assertFalse(PolarSpectatorPhasePolicy.eligibleSpectator(GameMode.CREATIVE, StaffRank.FOUNDER));
        assertFalse(PolarSpectatorPhasePolicy.eligibleSpectator(GameMode.SPECTATOR, null));
        assertFalse(PolarSpectatorPhasePolicy.eligibleSpectator(GameMode.SPECTATOR, StaffRank.SYSTEM));
    }

    @Test
    void onlyPhaseMitigationIsCancelled() {
        assertTrue(PolarSpectatorPhasePolicy.shouldCancelMitigation("PHASE", true));
        assertFalse(PolarSpectatorPhasePolicy.shouldCancelMitigation("MOVEMENT", true));
        assertFalse(PolarSpectatorPhasePolicy.shouldCancelMitigation("PHASE", false));
        assertFalse(PolarSpectatorPhasePolicy.shouldCancelMitigation(null, true));
    }
}

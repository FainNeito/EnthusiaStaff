package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

class HelperObserverPolicyTest {
    @Test
    void protectionsApplyOnlyToActiveHelpers() {
        assertTrue(HelperObserverPolicy.applies(true, StaffRank.HELPER));
        assertFalse(HelperObserverPolicy.applies(false, StaffRank.HELPER));
        assertFalse(HelperObserverPolicy.applies(true, StaffRank.MOD));
        assertFalse(HelperObserverPolicy.applies(true, StaffRank.ADMIN));
        assertFalse(HelperObserverPolicy.applies(true, StaffRank.FOUNDER));
        assertFalse(HelperObserverPolicy.applies(true, StaffRank.DEVELOPER));
        // Fail-closed: unresolvable rank during Staff Mode transition still applies protections.
        assertTrue(HelperObserverPolicy.applies(true, null));
        assertFalse(HelperObserverPolicy.applies(false, null));
    }

    @Test
    void helperBlocksOrdinaryRightClickAirUseOnly() {
        assertTrue(HelperObserverPolicy.blocksAirItemUse(
                true, StaffRank.HELPER, Action.RIGHT_CLICK_AIR, true
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                true, StaffRank.HELPER, Action.RIGHT_CLICK_AIR, false
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                true, StaffRank.HELPER, Action.LEFT_CLICK_AIR, true
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                true, StaffRank.HELPER, Action.RIGHT_CLICK_BLOCK, true
        ));
        assertFalse(HelperObserverPolicy.blocksAirItemUse(
                true, StaffRank.MOD, Action.RIGHT_CLICK_AIR, true
        ));
    }

    @Test
    void helperProjectileCollisionIsSuppressedOnlyForEntityHits() {
        assertTrue(HelperObserverPolicy.blocksProjectileCollision(true, StaffRank.HELPER, true));
        assertFalse(HelperObserverPolicy.blocksProjectileCollision(true, StaffRank.HELPER, false));
        assertFalse(HelperObserverPolicy.blocksProjectileCollision(false, StaffRank.HELPER, true));
        assertFalse(HelperObserverPolicy.blocksProjectileCollision(true, StaffRank.MOD, true));
    }

    @Test
    void helperCannotGainExperience() {
        assertEquals(0, HelperObserverPolicy.experienceAmount(true, StaffRank.HELPER, 25));
        assertEquals(25, HelperObserverPolicy.experienceAmount(false, StaffRank.HELPER, 25));
        assertEquals(25, HelperObserverPolicy.experienceAmount(true, StaffRank.MOD, 25));
    }
}

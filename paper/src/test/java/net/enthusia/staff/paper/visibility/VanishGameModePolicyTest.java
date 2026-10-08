package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class VanishGameModePolicyTest {
    @Test
    void dutyProfilesAreNotOverriddenByVanishRecovery() {
        assertTrue(VanishGameModePolicy.allows(StaffRank.HELPER, GameMode.SURVIVAL, true));
        assertEquals(GameMode.SURVIVAL,
                VanishGameModePolicy.reconcile(StaffRank.HELPER, GameMode.SURVIVAL, true));
        for (StaffRank rank : new StaffRank[]{StaffRank.MOD, StaffRank.DEVELOPER}) {
            assertTrue(VanishGameModePolicy.allows(rank, GameMode.SURVIVAL, true));
            assertFalse(VanishGameModePolicy.allows(rank, GameMode.SPECTATOR, true));
            assertEquals(GameMode.SURVIVAL,
                    VanishGameModePolicy.reconcile(rank, GameMode.SPECTATOR, true));
            assertEquals(GameMode.SURVIVAL,
                    VanishGameModePolicy.reconcile(rank, GameMode.CREATIVE, true));
        }
    }

    @Test
    void dutyCompositionPreservesAdminModesAndFailsClosedWithoutAuthority() {
        for (StaffRank rank : new StaffRank[]{StaffRank.ADMIN, StaffRank.FOUNDER}) {
            for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.SPECTATOR}) {
                assertEquals(mode, VanishGameModePolicy.reconcile(rank, mode, true));
            }
        }
        assertFalse(VanishGameModePolicy.allows(null, GameMode.CREATIVE, true));
        assertFalse(VanishGameModePolicy.allows(null, GameMode.SURVIVAL, true));
        assertEquals(GameMode.SPECTATOR, VanishGameModePolicy.reconcile(null, GameMode.CREATIVE, true));
        assertFalse(VanishGameModePolicy.allows(StaffRank.HELPER, GameMode.SURVIVAL, false));
        assertEquals(GameMode.SPECTATOR,
                VanishGameModePolicy.reconcile(StaffRank.MOD, GameMode.SURVIVAL, false));
    }

    @Test
    void founderAndAdminKeepTheirActualHotbarModesAndCanToggleSpectator() {
        for (StaffRank rank : new StaffRank[]{StaffRank.ADMIN, StaffRank.FOUNDER}) {
            for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.SPECTATOR}) {
                assertTrue(VanishGameModePolicy.allows(rank, mode));
                assertEquals(mode, VanishGameModePolicy.reconcile(rank, mode));
            }
            assertFalse(VanishGameModePolicy.allows(rank, GameMode.ADVENTURE));
        }
    }

    @Test
    void demotionOrMissingAuthorityCannotRestoreCreativeFromDurableVanish() {
        for (StaffRank rank : new StaffRank[]{StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER, null}) {
            assertFalse(VanishGameModePolicy.allows(rank, GameMode.CREATIVE));
            assertFalse(VanishGameModePolicy.allows(rank, GameMode.SURVIVAL));
            assertEquals(GameMode.SPECTATOR, VanishGameModePolicy.reconcile(rank, GameMode.CREATIVE));
        }
        assertFalse(VanishGameModePolicy.allows(StaffRank.SYSTEM, GameMode.SPECTATOR));
    }
}

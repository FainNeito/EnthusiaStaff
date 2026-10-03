package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class VanishGameModePolicyTest {
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

package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.potion.PotionEffect;
import org.bukkit.entity.Player;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

final class StaffStateCodecTest {
    @Test
    void deadPlayerCannotBeCapturedAsAStaffSnapshot() {
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isDead" -> true;
                    case "getHealth" -> 0.0D;
                    default -> throw new AssertionError("Snapshot accessed " + method.getName() + " before rejecting dead player");
                });
        assertThrows(IllegalStateException.class, () -> new StaffStateCodec().capture(player, "TEST"));
    }
    @Test
    void nonFiniteOrZeroHealthCannotBeCapturedAsAStaffSnapshot() {
        for (double health : new double[]{0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY}) {
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isDead" -> false;
                        case "getHealth" -> health;
                        default -> throw new AssertionError("Snapshot accessed " + method.getName() + " before rejecting invalid health");
                    });
            assertThrows(IllegalStateException.class, () -> new StaffStateCodec().capture(player, "TEST"));
        }
    }
    @Test
    void corruptedSnapshotIsRejectedBeforeReadingPlayerState() {
        assertThrows(IllegalStateException.class,
                () -> new StaffStateCodec().verifiedRestorationChecksum(null, "SMP", new byte[]{1}, "incorrect"));
    }
    @Test
    void infiniteStrengthSnapshotCanBeRestoredWithoutChangingItsAmplifier() {
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(PotionEffect.INFINITE_DURATION, 255));
    }

    @Test
    void finiteEffectsAndExpiredEffectsRemainSupported() {
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(1200, 0));
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(0, 0));
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(Integer.MAX_VALUE, 255));
    }

    @Test
    void invalidNegativeDurationsAndAmplifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> StaffStateCodec.validatePotionEffectValues(-2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StaffStateCodec.validatePotionEffectValues(Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StaffStateCodec.validatePotionEffectValues(PotionEffect.INFINITE_DURATION, -1));
    }
}

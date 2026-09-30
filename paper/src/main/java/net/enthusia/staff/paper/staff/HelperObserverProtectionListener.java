package net.enthusia.staff.paper.staff;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import java.util.Objects;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the Helper staff-mode profile observational instead of allowing normal survival participation.
 * Existing StaffModeManager and world-interaction guards continue to own damage, inventory, block,
 * pickup/drop and staff-tool session protections; this listener fills the Helper-specific gaps.
 */
public final class HelperObserverProtectionListener implements Listener {
    private final StaffModeManager staffMode;

    public HelperObserverProtectionListener(StaffModeManager staffMode) {
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAirItemUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        StaffRank rank = rank(event.getPlayer());
        if (HelperObserverPolicy.blocksAirItemUse(
                staffMode.active(event.getPlayer().getUniqueId()),
                rank,
                event.getAction(),
                item != null && !item.getType().isAir()
        )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && activeHelper(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player player) || !activeHelper(player)) {
            return;
        }
        event.setCancelled(true);
        clearMobTarget(event.getEntity(), player);
    }

    /**
     * Paper's modern ProjectileHitEvent has a documented firework exception. This deprecated event is
     * intentionally isolated to that one compatibility case because cancelling it explicitly lets the
     * firework continue flying instead of colliding with the Helper observer.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkCollision(ProjectileCollideEvent event) {
        if (event.getEntity() instanceof Firework
                && event.getCollidedWith() instanceof Player player
                && activeHelper(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && activeHelper(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && activeHelper(player)) {
            clearMobTarget(event.getDamager(), player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExperiencePickup(PlayerPickupExperienceEvent event) {
        if (activeHelper(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExperienceChange(PlayerExpChangeEvent event) {
        StaffRank rank = rank(event.getPlayer());
        event.setAmount(HelperObserverPolicy.experienceAmount(
                staffMode.active(event.getPlayer().getUniqueId()),
                rank,
                event.getAmount()
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        if (activeHelper(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent event) {
        if (activeHelper(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && activeHelper(player)) {
            event.setCancelled(true);
        }
    }

    private boolean activeHelper(Player player) {
        return HelperObserverPolicy.applies(staffMode.active(player.getUniqueId()), rank(player));
    }

    private static StaffRank rank(Player player) {
        return PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
    }

    private static void clearMobTarget(Object damager, Player target) {
        Mob mob = null;
        if (damager instanceof Mob direct) {
            mob = direct;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Mob shooter) {
            mob = shooter;
        }
        if (mob != null && target.equals(mob.getTarget())) {
            mob.setTarget(null);
        }
    }
}

package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Contains exceptional Staff Mode deaths and routes them into the durable Staff Mode exit lifecycle. */
public final class StaffModeDeathListener implements Listener {
    private static final long EXIT_RETRY_TICKS = 20L;
    private static final int JOIN_RECOVERY_ATTEMPTS = 120;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final Set<UUID> pendingDeathExits = ConcurrentHashMap.newKeySet();

    public StaffModeDeathListener(StaffModeManager staffMode) {
        this.plugin = JavaPlugin.getProvidingPlugin(StaffModeDeathListener.class);
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        StaffModeDeathPolicy.Action action = StaffModeDeathPolicy.decide(
                staffMode.active(playerId),
                staffMode.authorityActive(playerId)
        );
        if (action == StaffModeDeathPolicy.Action.IGNORE) {
            return;
        }

        contain(event);
        pendingDeathExits.add(playerId);
        if (action == StaffModeDeathPolicy.Action.CONTAIN_AND_EXIT) {
            staffMode.exit(player);
        }
        scheduleExitRetry(player, EXIT_RETRY_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void enforceContainedDeath(PlayerDeathEvent event) {
        if (staffMode.active(event.getPlayer().getUniqueId())) {
            contain(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (pendingDeathExits.contains(player.getUniqueId())) {
            scheduleJoinRecoveryCheck(player, JOIN_RECOVERY_ATTEMPTS);
        }
    }

    private void retryPendingExit(Player player) {
        UUID playerId = player.getUniqueId();
        if (!pendingDeathExits.contains(playerId)) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        if (!staffMode.active(playerId)) {
            pendingDeathExits.remove(playerId);
            return;
        }
        if (staffMode.authorityActive(playerId)) {
            staffMode.exit(player);
        }
        scheduleExitRetry(player, EXIT_RETRY_TICKS);
    }

    private void scheduleExitRetry(Player player, long delayTicks) {
        UUID playerId = player.getUniqueId();
        if (!player.getScheduler().execute(
                plugin,
                () -> retryPendingExit(player),
                () -> pendingDeathExits.add(playerId),
                delayTicks
        )) {
            pendingDeathExits.add(playerId);
        }
    }

    private void scheduleJoinRecoveryCheck(Player player, int attemptsRemaining) {
        UUID playerId = player.getUniqueId();
        if (!player.getScheduler().execute(
                plugin,
                () -> {
                    if (!pendingDeathExits.contains(playerId)) {
                        return;
                    }
                    if (staffMode.active(playerId)) {
                        retryPendingExit(player);
                        return;
                    }
                    if (attemptsRemaining <= 1) {
                        pendingDeathExits.remove(playerId);
                        return;
                    }
                    scheduleJoinRecoveryCheck(player, attemptsRemaining - 1);
                },
                () -> pendingDeathExits.add(playerId),
                5L
        )) {
            pendingDeathExits.add(playerId);
        }
    }

    static void contain(PlayerDeathEvent event) {
        event.setCancelled(true);
        AttributeInstance maximumHealth = event.getPlayer().getAttribute(Attribute.MAX_HEALTH);
        if (maximumHealth != null) {
            event.setReviveHealth(maximumHealth.getValue());
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.setShouldDropExperience(false);
        event.deathMessage(null);
        event.setShowDeathMessages(false);
        event.setShouldPlayDeathSound(false);
    }
}

package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Contains exceptional Staff Mode deaths and routes them into the durable Staff Mode exit lifecycle. */
public final class StaffModeDeathListener implements Listener {
    private final StaffModeManager staffMode;

    public StaffModeDeathListener(StaffModeManager staffMode) {
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        StaffModeDeathPolicy.Action action = StaffModeDeathPolicy.decide(
                staffMode.active(playerId),
                staffMode.authorityActive(playerId)
        );
        if (action == StaffModeDeathPolicy.Action.IGNORE) {
            return;
        }

        contain(event);
        if (action == StaffModeDeathPolicy.Action.CONTAIN_AND_EXIT) {
            staffMode.exit(event.getPlayer());
        }
    }

    static void contain(PlayerDeathEvent event) {
        event.setCancelled(true);
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

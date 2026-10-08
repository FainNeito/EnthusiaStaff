package net.enthusia.staff.paper.staff;

import java.time.Clock;
import net.enthusia.staff.domain.investigation.PlayerActivityTracker;
import net.enthusia.staff.domain.investigation.PlayerActivityTracker.Activity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PlayerActivityListener implements Listener, AutoCloseable {
    private final PlayerActivityTracker tracker;

    public PlayerActivityListener(Clock clock) { tracker = new PlayerActivityTracker(clock, 10_000); }
    public PlayerActivityTracker tracker() { return tracker; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        // Air clicks can carry Bukkit's default cancelled flag despite an actual input.
        if (event.getHand() != EquipmentSlot.HAND) { return; }
        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> tracker.record(event.getPlayer().getUniqueId(), Activity.LEFT_CLICK);
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> tracker.record(event.getPlayer().getUniqueId(), Activity.RIGHT_CLICK);
            default -> { }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) { tracker.record(event.getPlayer().getUniqueId(), Activity.CROUCH); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { tracker.record(event.getPlayer().getUniqueId(), Activity.BLOCK_PLACE); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { tracker.record(event.getPlayer().getUniqueId(), Activity.BLOCK_BREAK); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) { tracker.record(event.getPlayer().getUniqueId(), Activity.MOVE); }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { tracker.record(event.getPlayer().getUniqueId(), Activity.MOVE); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { tracker.forget(event.getPlayer().getUniqueId()); }
    @Override public void close() { tracker.clear(); }
}

package net.enthusia.staff.paper.punishment;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class PunishmentTargetPickerHolder implements InventoryHolder {
    private final UUID viewerId;
    private final String commandName;
    private final int page;
    private final List<UUID> targetIds;
    private Inventory inventory;

    PunishmentTargetPickerHolder(UUID viewerId, String commandName, int page, List<UUID> targetIds) {
        if (viewerId == null || commandName == null || commandName.isBlank() || page < 0 || targetIds == null) {
            throw new IllegalArgumentException("punishment target picker fields must be present");
        }
        this.viewerId = viewerId;
        this.commandName = commandName;
        this.page = page;
        this.targetIds = List.copyOf(targetIds);
    }

    UUID viewerId() {
        return viewerId;
    }

    String commandName() {
        return commandName;
    }

    int page() {
        return page;
    }

    List<UUID> targetIds() {
        return targetIds;
    }

    void attach(Inventory inventory) {
        if (inventory == null || this.inventory != null) {
            throw new IllegalStateException("punishment target picker inventory may be attached exactly once");
        }
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("punishment target picker inventory has not been attached");
        }
        return inventory;
    }
}

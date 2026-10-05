package net.enthusia.staff.paper.staff;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** A complete permutation of supported hotbar tools; no arbitrary commands or tool IDs. */
final class StaffToolLayout {
    private final Map<StaffToolDefinition, Integer> slots;

    private StaffToolLayout(Map<StaffToolDefinition, Integer> slots) { this.slots = Map.copyOf(slots); }

    static StaffToolLayout load(ConfigurationSection configuration) {
        EnumMap<StaffToolDefinition, Integer> slots = new EnumMap<>(StaffToolDefinition.class);
        HashSet<Integer> occupied = new HashSet<>();
        ConfigurationSection section = configuration == null ? null : configuration.getConfigurationSection("staff-tools.slots");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (StaffToolDefinition.fromId(key).isEmpty()) {
                    throw new IllegalArgumentException("Unknown staff tool slot ID: " + key);
                }
                if (!section.isInt(key)) { throw new IllegalArgumentException("Staff tool slot must be an integer: " + key); }
            }
        }
        for (StaffToolDefinition tool : StaffToolDefinition.values()) {
            int slot = section == null ? tool.slot() : section.getInt(tool.id(), tool.slot());
            if (slot < 0 || slot > 8 || !occupied.add(slot)) {
                throw new IllegalArgumentException("Staff tool slots must be unique values from 0 to 8");
            }
            slots.put(tool, slot);
        }
        return new StaffToolLayout(slots);
    }

    int slot(StaffToolDefinition tool) { return slots.get(tool); }
}

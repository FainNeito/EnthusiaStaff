package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StaffToolLayoutTest {
    @Test void swapsPreserveProtectedSlotValidation() {
        var config = new YamlConfiguration();
        config.set("staff-tools.slots.random-teleport", 1);
        config.set("staff-tools.slots.player-inspector", 0);
        var layout = StaffToolLayout.load(config);
        UUID owner = UUID.randomUUID();
        var tool = StaffToolDefinition.RANDOM_TELEPORT;
        assertEquals(StaffToolSessionPolicy.Status.VALID, StaffToolSessionPolicy.validate(owner, "session", 1,
                tool, tool.material(), owner.toString(), "session", StaffRank.ADMIN, layout.slot(tool)));
        assertEquals(StaffToolSessionPolicy.Status.SLOT_MISMATCH, StaffToolSessionPolicy.validate(owner, "session", 0,
                tool, tool.material(), owner.toString(), "session", StaffRank.ADMIN, layout.slot(tool)));
        assertEquals(StaffToolSessionPolicy.Status.OWNER_MISMATCH, StaffToolSessionPolicy.validate(owner, "session", 1,
                tool, tool.material(), UUID.randomUUID().toString(), "session", StaffRank.ADMIN, layout.slot(tool)));
    }
    @Test void refusesDuplicateOutOfRangeUnknownAndNonIntegerSlots() {
        for (Object value : new Object[]{1, 9, -1, "0"}) {
            var config = new YamlConfiguration();
            config.set("staff-tools.slots.random-teleport", value);
            assertThrows(IllegalArgumentException.class, () -> StaffToolLayout.load(config));
        }
        var unknown = new YamlConfiguration();
        unknown.set("staff-tools.slots.console-command", 0);
        assertThrows(IllegalArgumentException.class, () -> StaffToolLayout.load(unknown));
    }
}

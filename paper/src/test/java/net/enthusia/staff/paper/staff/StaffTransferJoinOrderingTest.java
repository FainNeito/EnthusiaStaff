package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;

class StaffTransferJoinOrderingTest {
    @Test
    void destinationStateCapturePrecedesTransferredVanishApplication() throws Exception {
        Method staffJoin = StaffModeManager.class.getMethod("onJoin", PlayerJoinEvent.class);
        Method transferJoin = StaffTransferJoinListener.class.getMethod("onJoin", PlayerJoinEvent.class);

        assertEquals(EventPriority.LOWEST, staffJoin.getAnnotation(EventHandler.class).priority());
        assertEquals(EventPriority.LOW, transferJoin.getAnnotation(EventHandler.class).priority());
    }
}

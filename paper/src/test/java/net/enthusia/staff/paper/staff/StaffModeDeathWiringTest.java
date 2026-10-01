package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeDeathWiringTest {
    @Test
    void paperRuntimeRegistersDeathContainmentListener() throws IOException {
        String runtime = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"
        ));

        assertTrue(
                runtime.contains("new StaffModeDeathListener(staffMode)"),
                "Staff Mode death containment must remain registered in the Paper runtime"
        );
    }

    @Test
    void deathListenerContainsBeforeNormalDeathLifecycleAndUsesDurableExit() throws IOException {
        String listener = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/staff/StaffModeDeathListener.java"
        ));

        assertTrue(listener.contains("@EventHandler(priority = EventPriority.LOWEST)"));
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.HIGHEST)"));
        assertTrue(listener.contains("StaffModeDeathPolicy.decide("));
        assertTrue(listener.contains("staffMode.authorityActive(playerId)"));
        assertTrue(listener.contains("event.setCancelled(true)"));
        assertTrue(listener.contains("event.setReviveHealth(maximumHealth.getValue())"));
        assertTrue(listener.contains("event.setKeepInventory(true)"));
        assertTrue(listener.contains("event.getDrops().clear()"));
        assertTrue(listener.contains("event.setKeepLevel(true)"));
        assertTrue(listener.contains("event.setDroppedExp(0)"));
        assertTrue(listener.contains("event.setShouldDropExperience(false)"));
        assertTrue(listener.contains("event.deathMessage(null)"));
        assertTrue(listener.contains("event.setShowDeathMessages(false)"));
        assertTrue(listener.contains("event.setShouldPlayDeathSound(false)"));
        assertTrue(listener.contains("staffMode.exit(event.getPlayer())"));
    }

    @Test
    void cheatTesterTreatsOnlyCompletedDeathsAsRecoveryTriggers() throws IOException {
        String lifecycle = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/tester/CheatTesterLifecycleListener.java"
        ));

        assertTrue(
                lifecycle.contains("@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n"
                        + "    public void onDeath(PlayerDeathEvent event)"),
                "Cancelled Staff Mode death events must not retire cheat-tester sessions"
        );
    }

    private static Path paperModule() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))) {
            return current;
        }
        Path paper = current.resolve("paper");
        if (Files.exists(paper.resolve("src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))) {
            return paper;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }
}

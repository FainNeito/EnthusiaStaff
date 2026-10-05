package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffModeEntryVanishWiringTest {
    @Test void freshEntryEnablesVanishAfterSuccessfulPublicationWithoutChangingRecoveryOrHandoff() throws IOException {
        String source = read("staff/StaffModeManager.java");
        assertEquals(2, source.split("activateFreshSession", -1).length - 1);
        String fresh = source.substring(source.indexOf("private void activateFreshSession"),
                source.indexOf("public void exit("));
        assertTrue(fresh.indexOf("activateDurableSession(") < fresh.indexOf("entryListener.accept(player)"));
        assertTrue(fresh.contains("if (active.get(playerId) == session)"));
        assertTrue(read("PaperRuntimeComponents.java").contains("staffMode.setEntryListener(vanish::staffModeEntered)"));
    }

    @Test void automaticEntryIsEnableOnlyAndUsesExistingDurableVanishPath() throws IOException {
        String source = read("visibility/VanishManager.java");
        String entry = source.substring(source.indexOf("public void staffModeEntered("),
                source.indexOf("public void configureSpectatorTab("));
        assertTrue(entry.contains("staffMode.active(player.getUniqueId())"));
        assertTrue(entry.contains("!isVanished(player.getUniqueId())"));
        assertTrue(entry.contains("set(player, rank, true, true)"));
        assertFalse(entry.contains("toggle("));
        assertTrue(entry.contains("sessionId.equals(staffMode.activeSessionId(playerId))"));
        assertTrue(entry.contains("staffMode.exit(current)"));
    }

    private static String read(String suffix) throws IOException {
        return Files.readString(Path.of("src/main/java/net/enthusia/staff/paper", suffix))
                .replace("\r\n", "\n");
    }
}

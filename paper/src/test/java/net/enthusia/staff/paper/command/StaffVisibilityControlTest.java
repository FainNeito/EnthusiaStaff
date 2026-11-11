package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StaffVisibilityControlTest {
    @Test
    void visibilityControlsRemainBehindStaffPermissionAndUseCurrentManagers() throws IOException {
        String source = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/command/StaffModeCommand.java"
        ));
        int permission = source.indexOf("if (!allowed(sender))");
        int tab = source.indexOf("if (spectatorTabRequested(arguments))");
        int toggle = source.indexOf("if (vanishToggleRequested(arguments))");

        assertTrue(permission >= 0);
        assertTrue(tab > permission);
        assertTrue(toggle > permission);
        assertTrue(source.contains("vanish.configureSpectatorTab(player, true)"));
        assertTrue(source.contains("vanish.configureSpectatorTab(player, false)"));
        assertTrue(source.contains("vanish.toggle(player)"));
    }

    private static Path paperModule() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("paper/src/main/java/net/enthusia/staff/paper/command/StaffModeCommand.java"))) {
            return current.resolve("paper");
        }
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/command/StaffModeCommand.java"))) {
            return current;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }
}

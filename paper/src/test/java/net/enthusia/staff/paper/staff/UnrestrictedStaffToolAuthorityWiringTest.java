package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UnrestrictedStaffToolAuthorityWiringTest {
    @Test
    void staffToolAuthorizationAcceptsExplicitUnrestrictedIdentityWithoutActiveSession() throws IOException {
        String manager = source("src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java");
        int start = manager.indexOf("boolean authorizedForTool");
        int end = manager.indexOf("private StaffRank rankForAction", start);
        String method = manager.substring(start, end);

        assertTrue(method.contains("boolean unrestricted = isUnrestricted(player)"));
        assertTrue(method.contains("if (!activeSession && !unrestricted)"));
        assertTrue(method.contains("PaperStaffRankResolver.resolve(player::hasPermission)"));
    }

    @Test
    void commandToolsUseActiveOrUnrestrictedAuthority() throws IOException {
        String dispatcher = source("src/main/java/net/enthusia/staff/paper/staff/StaffToolDispatcher.java");

        assertTrue(dispatcher.contains("staffMode.authorityActiveOrUnrestricted(player.getUniqueId())"));
    }

    @Test
    void fakeBaseControllerUsesActiveOrUnrestrictedAuthorityThroughoutLifecycle() throws IOException {
        String fakeBase = source("src/main/java/net/enthusia/staff/paper/tester/FakeBaseManager.java");

        assertTrue(fakeBase.contains(
                "staffMode.authorityActiveOrUnrestricted(staff.getUniqueId())"
        ));
        assertTrue(fakeBase.contains(
                "staffMode.authorityActiveOrUnrestricted(staffId)"
        ));
        assertTrue(fakeBase.contains(
                "staffMode.authorityActiveOrUnrestricted(operation.staffId)"
        ));
    }

    private static String source(String relative) throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path file = Files.exists(current.resolve(relative))
                ? current.resolve(relative)
                : current.resolve("paper").resolve(relative.replaceFirst("^src/", "src/"));
        return Files.readString(file).replace("\r\n", "\n");
    }
}

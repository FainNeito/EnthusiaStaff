package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.OperationalMode;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

final class StaffVisibilityControlTest {
    @Test
    void toggleDelegatesToExistingVisibilityExecutorWithNoArguments() {
        CommandSender actor = sender(true);
        AtomicInteger calls = new AtomicInteger();
        StaffModeCommand command = new StaffModeCommand(() -> OperationalMode.ACTIVE, null,
                (sender, ignored, label, arguments) -> {
                    assertSame(actor, sender);
                    assertEquals("vanish", label);
                    assertArrayEquals(new String[0], arguments);
                    calls.incrementAndGet();
                    return true;
                });
        assertTrue(command.onCommand(actor, null, "staff", new String[]{"togglevanish"}));
        assertEquals(1, calls.get());
    }

    @Test
    void tabPreservesArgumentsForExistingPermissionAndRankChecks() {
        CommandSender actor = sender(true);
        AtomicInteger calls = new AtomicInteger();
        StaffModeCommand command = new StaffModeCommand(() -> OperationalMode.MAINTENANCE, null,
                (sender, ignored, label, arguments) -> {
                    assertSame(actor, sender);
                    assertArrayEquals(new String[]{"tab", "show"}, arguments);
                    calls.incrementAndGet();
                    return true;
                });
        assertTrue(command.onCommand(actor, null, "staff", new String[]{"tab", "show"}));
        assertEquals(1, calls.get());
    }

    @Test
    void missingStaffPermissionNeverInvokesVisibilityControls() {
        AtomicInteger calls = new AtomicInteger();
        StaffModeCommand command = new StaffModeCommand(() -> OperationalMode.ACTIVE, null,
                (sender, ignored, label, arguments) -> { calls.incrementAndGet(); return true; });
        assertTrue(command.onCommand(sender(false), null, "staff", new String[]{"togglevanish"}));
        assertEquals(0, calls.get());
    }

    private static CommandSender sender(boolean allowed) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")) {
                        return allowed && "enthusiastaff.staffmode".equals(arguments[0]);
                    }
                    return null;
                });
    }
}

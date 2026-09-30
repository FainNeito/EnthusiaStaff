package net.enthusia.staff.paper.auth;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers and invalidates the LuckPerms active-duty context for Staff Mode. */
public final class LuckPermsStaffDutyContext implements AutoCloseable {
    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final StaffModeManager staffMode;
    private final StaffDutyContextCalculator calculator;
    private final AtomicBoolean closed = new AtomicBoolean();

    private LuckPermsStaffDutyContext(JavaPlugin plugin, LuckPerms luckPerms, StaffModeManager staffMode) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.staffMode = Objects.requireNonNull(staffMode, "staffMode");
        this.calculator = new StaffDutyContextCalculator(staffMode::authorityActive);
    }

    public static LuckPermsStaffDutyContext install(JavaPlugin plugin, StaffModeManager staffMode) {
        LuckPermsStaffDutyContext registration = new LuckPermsStaffDutyContext(
                plugin,
                LuckPermsProvider.get(),
                staffMode
        );
        registration.luckPerms.getContextManager().registerCalculator(registration.calculator);
        staffMode.setAuthorityContextListener(registration::signalContextUpdate);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            registration.signalContextUpdate(player);
        }
        return registration;
    }

    private void signalContextUpdate(Player player) {
        if (!closed.get()) {
            luckPerms.getContextManager().signalContextUpdate(player);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        staffMode.setAuthorityContextListener(ignored -> {
        });
        luckPerms.getContextManager().unregisterCalculator(calculator);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPerms.getContextManager().signalContextUpdate(player);
        }
    }
}

package net.enthusia.staff.paper.command;

import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.enthusia.staff.paper.staff.StaffModeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public final class StaffModeCommand implements CommandExecutor {
    private static final String PERMISSION = "enthusiastaff.staffmode";

    private final Supplier<OperationalMode> mode;
    private final StaffModeManager manager;

    public StaffModeCommand(Supplier<OperationalMode> mode, StaffModeManager manager) {
        this.mode = mode;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(
                sender,
                PERMISSION,
                "You do not have permission to use staff mode."
        )) {
            return true;
        }
        if (arguments.length == 2 && "recover".equalsIgnoreCase(arguments[0])) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(StaffMessageStyle.error("Targeted snapshot recovery is available only from the server console."));
                return true;
            }
            Player target = sender.getServer().getPlayerExact(arguments[1]);
            if (target == null) {
                sender.sendMessage(StaffMessageStyle.error("That player must be online on the snapshot's owning backend."));
                return true;
            }
            manager.recover(target);
            sender.sendMessage(StaffMessageStyle.info("Durable snapshot recovery requested for " + target.getName() + '.'));
            return true;
        }
        if (arguments.length > 0 && !recoveryRequested(arguments)) {
            sender.sendMessage(StaffMessageStyle.usage("Usage: /staff [recover]"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can enter staff mode."));
            return true;
        }
        if (recoveryRequested(arguments)) {
            player.sendMessage(StaffMessageStyle.style(
                    "Checking your durable staff-mode snapshot and repairing local state..."
            ));
            manager.recover(player);
            return true;
        }
        boolean activeSession = manager.active(player.getUniqueId());
        OperationalMode currentMode = mode.get();
        if (!StaffOperationalModeGate.staffModeTransitionAllowed(currentMode, activeSession)) {
            player.sendMessage(StaffMessageStyle.style(
                    "Staff-mode entry is disabled while moderation is " + currentMode + '.'
            ));
            return true;
        }
        if (activeSession) {
            manager.exit(player);
            return true;
        }
        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
        if (rank == null) {
            player.sendMessage(StaffMessageStyle.style(
                    "An explicit EnthusiaStaff rank is required before entering staff mode."
            ));
            return true;
        }
        manager.enter(player, rank);
        return true;
    }

    private static boolean recoveryRequested(String[] arguments) {
        return arguments.length == 1 && "recover".equalsIgnoreCase(arguments[0]);
    }
}

package net.enthusia.staff.paper.command;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.config.MessageCatalog;
import net.enthusia.staff.paper.config.MessageKey;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class VanishCommand implements CommandExecutor {
    private static final String PERMISSION = "enthusiastaff.vanish";

    private final Supplier<OperationalMode> mode;
    private final VanishManager vanish;
    private volatile Supplier<MessageCatalog> messages = MessageCatalog::builtIn;

    public VanishCommand(Supplier<OperationalMode> mode, VanishManager vanish) {
        this.mode = mode;
        this.vanish = vanish;
    }

    public void configureMessages(Supplier<MessageCatalog> messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(sender, PERMISSION, message(MessageKey.VANISH_PERMISSION_DENIED))) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message(MessageKey.VANISH_PLAYER_ONLY));
            return true;
        }
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("tab")) {
            if (arguments[1].equalsIgnoreCase("show")) {
                vanish.configureSpectatorTab(player, true);
                return true;
            }
            if (arguments[1].equalsIgnoreCase("hide")) {
                vanish.configureSpectatorTab(player, false);
                return true;
            }
        }
        if (arguments.length != 0) {
            player.sendMessage(message(
                    MessageKey.VANISH_USAGE,
                    Map.of("label", label, "choices", "<show|hide>")
            ));
            return true;
        }
        OperationalMode currentMode = mode.get();
        boolean currentlyVanished = vanish.isVanished(player.getUniqueId());
        if (!StaffOperationalModeGate.vanishChangeAllowed(currentMode, currentlyVanished)) {
            player.sendMessage(StaffMessageStyle.warning(message(
                    MessageKey.VANISH_MODE_DISABLED,
                    Map.of("mode", currentMode)
            )));
            return true;
        }
        vanish.toggle(player);
        return true;
    }

    private Component message(MessageKey key) {
        return Objects.requireNonNull(messages.get(), "message catalog").component(key);
    }

    private Component message(MessageKey key, Map<String, ?> values) {
        return Objects.requireNonNull(messages.get(), "message catalog").component(key, values);
    }
}

package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.enthusia.staff.paper.staff.StaffModeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Shared name completion without reading other Folia entities or doing synchronous database work. */
public final class PlayerNameCompletion implements Listener {
    private final Map<UUID, String> online = new ConcurrentHashMap<>();
    private final Object namesLock = new Object();
    private final BiPredicate<UUID, UUID> visible;
    private final StaffModeManager sessions;

    public PlayerNameCompletion(BiPredicate<UUID, UUID> visible, StaffModeManager sessions) {
        this.visible = visible;
        this.sessions = sessions;
    }

    public void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getCommandMap().getKnownCommands().values().stream()
                .filter(PluginCommand.class::isInstance).map(PluginCommand.class::cast)
                .filter(command -> command.getPlugin() == plugin).distinct().forEach(command -> {
            if (!PlayerArgumentRoutes.supports(command.getName().toLowerCase(Locale.ROOT))) { return; }
            TabCompleter prior = command.getTabCompleter();
            if (command.getExecutor() instanceof InventoryCommand inventory) {
                inventory.setOnlineNames(() -> List.copyOf(online.values()));
            }
            command.setTabCompleter((sender, route, alias, args) -> complete(sender, route, alias, args, prior));
        });
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> {
                    if (player.isOnline()) { remember(player); }
                }, () -> { }, 1L);
            }
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { remember(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { online.remove(event.getPlayer().getUniqueId()); }

    void remember(Player player) {
        synchronized (namesLock) {
            if (online.size() < 10_000 || online.containsKey(player.getUniqueId())) {
                online.put(player.getUniqueId(), player.getName());
            }
        }
    }

    List<String> complete(CommandSender sender, Command command, String alias, String[] args,
            TabCompleter prior) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        PlayerArgumentRoutes.Route route = PlayerArgumentRoutes.find(name, args);
        if (route == null) {
            return prior == null ? List.of() : nonNull(prior.onTabComplete(sender, command, alias, args));
        }
        boolean console = sender instanceof ConsoleCommandSender;
        if (!route.allowed(sender::hasPermission, console) || !baseAllowed(sender, command, name)) {
            return List.of();
        }
        boolean inventoryRoute = name.equals("invsee") || name.equals("endersee");
        List<String> delegated = inventoryRoute && prior != null
                ? nonNull(prior.onTabComplete(sender, command, alias, args)) : route.keywords().stream()
                        .filter(keyword -> keywordAllowed(sender, name, keyword)).toList();
        List<String> candidates = new ArrayList<>();
        Predicate<UUID> allowed = id -> !(sender instanceof Player viewer) || visible.test(viewer.getUniqueId(), id);
        online.forEach((id, username) -> { if (allowed.test(id)) { candidates.add(username); } });
        // Preserve the existing async offline inventory cache, while filtering any hidden online names.
        if (inventoryRoute) {
            candidates.addAll(delegated.stream().filter(value -> online.entrySet().stream()
                    .noneMatch(entry -> entry.getValue().equalsIgnoreCase(value) && !allowed.test(entry.getKey())))
                    .toList());
        } else {
            candidates.addAll(delegated.stream().filter(route.keywords()::contains).toList());
        }
        return matches(candidates, args[args.length - 1]);
    }

    private static boolean keywordAllowed(CommandSender sender, String name, String keyword) {
        if (!name.equals("inspect")) { return true; }
        PlayerArgumentRoutes.Route action = PlayerArgumentRoutes.find(name, new String[]{keyword, ""});
        return action != null && action.allowed(sender::hasPermission, sender instanceof ConsoleCommandSender);
    }

    private boolean baseAllowed(CommandSender sender, Command command, String name) {
        if (!command.testPermissionSilent(sender)) { return false; }
        if (name.equals("inspect") && !sender.hasPermission("enthusiastaff.inspect")) { return false; }
        if (name.equals("stafftools")) {
            return sender instanceof Player player && sessions.active(player.getUniqueId());
        }
        return true;
    }

    private static List<String> nonNull(List<String> values) { return values == null ? List.of() : values; }

    static List<String> matches(List<String> names, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(50).toList();
    }
}

package net.enthusia.staff.paper.integration;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.PaperStaffRankResolver;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional Polar compatibility boundary.
 *
 * <p>Polar's loader callback is prepared during plugin load, while Bukkit/Folia player state is
 * mirrored into a concurrent UUID set from normal player lifecycle events after Staff enables.
 * The Polar callback reads only that set and never touches Bukkit player state.</p>
 */
public final class PolarSpectatorPhaseCompatibility {
    private static final String FEATURE_KEY = "polar-spectator-phase";
    private static final String POLAR_LOADER = "PolarLoader";
    private static final String HOOK_CLASS =
            "net.enthusia.staff.paper.integration.PolarSpectatorPhaseHook";
    private static final Set<UUID> ELIGIBLE_SPECTATORS = ConcurrentHashMap.newKeySet();

    private PolarSpectatorPhaseCompatibility() {
    }

    public static void prepareOnLoad(JavaPlugin plugin, Map<String, String> featureIssues) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(featureIssues, "featureIssues");
        if (plugin.getServer().getPluginManager().getPlugin(POLAR_LOADER) == null) {
            featureIssues.remove(FEATURE_KEY);
            return;
        }
        try {
            Class<?> hook = Class.forName(HOOK_CLASS);
            hook.getMethod("registerEnableCallback", JavaPlugin.class).invoke(null, plugin);
            featureIssues.remove(FEATURE_KEY);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            featureIssues.put(
                    FEATURE_KEY,
                    "Polar is present but its Spectator phase compatibility callback could not be prepared"
            );
            plugin.getLogger().log(
                    Level.WARNING,
                    "Polar Spectator phase compatibility callback could not be prepared",
                    unwrap(failure)
            );
        }
    }

    public static void installEligibilityTracking(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        ELIGIBLE_SPECTATORS.clear();
        if (!plugin.getServer().getPluginManager().isPluginEnabled(POLAR_LOADER)) {
            return;
        }
        EligibilityListener listener = new EligibilityListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        reconcileOnlinePlayers(plugin, listener);
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                ignored -> reconcileOnlinePlayers(plugin, listener),
                1L,
                2L
        );
    }

    private static void reconcileOnlinePlayers(JavaPlugin plugin, EligibilityListener listener) {
        Set<UUID> online = ConcurrentHashMap.newKeySet();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
            listener.refresh(player, player.getGameMode());
        }
        ELIGIBLE_SPECTATORS.retainAll(online);
    }

    static boolean eligible(UUID playerId) {
        return playerId != null && ELIGIBLE_SPECTATORS.contains(playerId);
    }

    public static void close(JavaPlugin plugin) {
        ELIGIBLE_SPECTATORS.clear();
        if (plugin == null || plugin.getServer().getPluginManager().getPlugin(POLAR_LOADER) == null) {
            return;
        }
        try {
            Class<?> hook = Class.forName(HOOK_CLASS);
            hook.getMethod("close").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            plugin.getLogger().log(Level.FINE, "Polar Spectator compatibility cleanup failed", unwrap(failure));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return failure;
    }

    private static final class EligibilityListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onGameModeChange(PlayerGameModeChangeEvent event) {
            refresh(event.getPlayer(), event.getNewGameMode());
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            refresh(event.getPlayer(), event.getPlayer().getGameMode());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            ELIGIBLE_SPECTATORS.remove(event.getPlayer().getUniqueId());
        }

        private void refresh(Player player, GameMode gameMode) {
            StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);
            if (PolarSpectatorPhasePolicy.eligibleSpectator(gameMode, rank)) {
                ELIGIBLE_SPECTATORS.add(player.getUniqueId());
            } else {
                ELIGIBLE_SPECTATORS.remove(player.getUniqueId());
            }
        }
    }
}

package net.enthusia.staff.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.slf4j.Logger;

/** Optional public Velocitab API integration, without bundling another plugin's classes. */
final class VelocitabStaffBridge implements AutoCloseable {
    private static final int MAX_VANISHED = 10_000;
    private static final long MAX_AGE = Duration.ofSeconds(5).toNanos();
    private final ProxyServer proxy;
    private final Logger logger;
    private final Supplier<MariaDbRuntime> storage;
    private final Executor workers;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<UUID, NameOverride> names = new HashMap<>();
    private final Object api;
    private final Object previous;
    private final Object integration;
    private final Class<?> integrationType;
    private ScheduledTask task;
    private volatile StaffTabPresence presence;
    private volatile long verifiedAt;
    private volatile boolean closed;
    private Map<UUID, StaffRank> lastRanks = Map.of();
    private Set<UUID> lastPlayers = Set.of();
    private Set<UUID> lastUsers = Set.of();
    private boolean failureReported;

    static Optional<VelocitabStaffBridge> start(Object owner, ProxyServer proxy, Logger logger,
            Supplier<MariaDbRuntime> storage, Executor workers) {
        Optional<Object> plugin = proxy.getPluginManager().getPlugin("velocitab")
                .flatMap(container -> container.getInstance());
        if (plugin.isEmpty()) {
            return Optional.empty();
        }
        try {
            VelocitabStaffBridge bridge = new VelocitabStaffBridge(proxy, logger, storage, workers,
                    plugin.get().getClass().getClassLoader());
            bridge.task = proxy.getScheduler().buildTask(owner, bridge::refresh)
                    .repeat(1, TimeUnit.SECONDS).schedule();
            logger.info("Velocitab staff visibility and state markers connected");
            return Optional.of(bridge);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.error("Velocitab staff integration unavailable ({})", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private VelocitabStaffBridge(ProxyServer proxy, Logger logger, Supplier<MariaDbRuntime> storage,
            Executor workers, ClassLoader loader) throws ReflectiveOperationException {
        this(proxy, logger, storage, workers,
                Class.forName("net.william278.velocitab.api.VelocitabAPI", true, loader)
                        .getMethod("getInstance").invoke(null),
                Class.forName("net.william278.velocitab.vanish.VanishIntegration", true, loader));
    }

    VelocitabStaffBridge(ProxyServer proxy, Logger logger, Supplier<MariaDbRuntime> storage,
            Executor workers, Object api, Class<?> integrationType) throws ReflectiveOperationException {
        this.proxy = proxy;
        this.logger = logger;
        this.storage = storage;
        this.workers = workers;
        this.api = api;
        this.integrationType = integrationType;
        previous = call(api, "getVanishIntegration");
        integration = java.lang.reflect.Proxy.newProxyInstance(integrationType.getClassLoader(), new Class<?>[]{integrationType},
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "canSee" -> canSee((String) arguments[0], (String) arguments[1]);
                    case "isVanished" -> isVanished((String) arguments[0]);
                    case "toString" -> "EnthusiaStaff visibility";
                    case "hashCode" -> System.identityHashCode(ignored);
                    case "equals" -> ignored == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        api.getClass().getMethod("setVanishIntegration", integrationType).invoke(api, integration);
    }

    private boolean fresh() {
        return presence != null && System.nanoTime() - verifiedAt <= MAX_AGE;
    }

    private boolean canSee(String viewerName, String targetName) throws ReflectiveOperationException {
        Optional<Player> viewer = proxy.getPlayer(viewerName);
        Optional<Player> target = proxy.getPlayer(targetName);
        if (viewer.isEmpty() || target.isEmpty()) {
            return false;
        }
        if (viewer.get().getUniqueId().equals(target.get().getUniqueId())) {
            return true;
        }
        return fresh() && presence.canSee(viewer.get().getUniqueId(), rank(viewer.get()),
                target.get().getUniqueId(), rank(target.get()))
                && (boolean) integrationType.getMethod("canSee", String.class, String.class)
                        .invoke(previous, viewerName, targetName);
    }

    private boolean isVanished(String name) throws ReflectiveOperationException {
        Optional<Player> player = proxy.getPlayer(name);
        return !fresh() || player.isEmpty() || presence.vanished().containsKey(player.get().getUniqueId())
                || (boolean) integrationType.getMethod("isVanished", String.class).invoke(previous, name);
    }

    private void refresh() {
        if (closed || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            workers.execute(() -> {
                try {
                    refreshVerified();
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    // Never publish an empty vanished set after a storage failure.
                    if (!failureReported) {
                        logger.warn("Staff tab presence refresh unavailable; visibility fails closed ({})",
                                exception.getClass().getSimpleName());
                        failureReported = true;
                    }
                    if (!fresh()) {
                        hideUnavailablePresence();
                    }
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException exception) {
            running.set(false);
        }
    }

    private synchronized void refreshVerified() throws ReflectiveOperationException {
        if (closed) {
            return;
        }
        MariaDbRuntime runtime = storage.get();
        if (runtime == null) {
            hideUnavailablePresence();
            return;
        }
        var records = loadVanished(runtime.vanishStore());
        Map<UUID, StaffRank> vanished = new HashMap<>();
        records.forEach(record -> vanished.put(record.staffId(), record.rank()));
        Set<UUID> staffMode = new HashSet<>();
        Map<UUID, StaffRank> ranks = new HashMap<>();
        var players = java.util.List.copyOf(proxy.getAllPlayers());
        Set<UUID> playerIds = new HashSet<>();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            playerIds.add(id);
            StaffRank rank = rank(player);
            if (rank != null) {
                ranks.put(id, rank);
                runtime.staffSessionStore().active(id)
                        .filter(session -> session.state() == StaffSessionState.ACTIVE)
                        .ifPresent(session -> staffMode.add(id));
            }
        }
        StaffTabPresence updated = new StaffTabPresence(vanished, staffMode);
        Object list = call(api, "getTabList");
        Object vanishList = call(list, "getVanishTabList");
        Map<UUID, Object> users = new HashMap<>();
        for (Player player : players) {
            Optional<?> user = (Optional<?>) api.getClass().getMethod("getUser", Player.class).invoke(api, player);
            if (user.isPresent() && (boolean) call(user.get(), "isLoaded")) {
                users.put(player.getUniqueId(), user.get());
            }
        }
        boolean changed = !updated.equals(presence) || !ranks.equals(lastRanks) || !playerIds.equals(lastPlayers)
                || !users.keySet().equals(lastUsers) || !fresh();
        presence = updated;
        verifiedAt = System.nanoTime();
        failureReported = false;
        lastRanks = Map.copyOf(ranks);
        lastPlayers = Set.copyOf(playerIds);
        lastUsers = Set.copyOf(users.keySet());
        for (Player player : players) {
            Object user = users.get(player.getUniqueId());
            if (user == null) {
                continue;
            }
            updateName(player, updated.marker(player.getUniqueId()));
            if (changed) {
                Method recalculate = vanishList.getClass().getMethod("recalculateVanishForPlayer", user.getClass());
                recalculate.invoke(vanishList, user);
            }
        }
        names.keySet().retainAll(playerIds);
    }

    private synchronized void hideUnavailablePresence() {
        if (closed || fresh()) {
            return;
        }
        try {
            Object vanishList = call(call(api, "getTabList"), "getVanishTabList");
            for (Player player : proxy.getAllPlayers()) {
                Optional<?> user = (Optional<?>) api.getClass().getMethod("getUser", Player.class).invoke(api, player);
                if (user.isPresent()) {
                    vanishList.getClass().getMethod("recalculateVanishForPlayer", user.get().getClass())
                            .invoke(vanishList, user.get());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.debug("Unavailable staff presence could not refresh tab entries ({})",
                    exception.getClass().getSimpleName());
        }
    }

    private void updateName(Player player, String marker) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        Optional<String> current = (Optional<String>) api.getClass().getMethod("getCustomPlayerName", Player.class)
                .invoke(api, player);
        NameOverride owned = names.get(player.getUniqueId());
        Optional<String> original = owned != null && current.equals(Optional.of(owned.applied()))
                ? owned.original() : current;
        if (marker.isEmpty()) {
            if (owned != null && current.equals(Optional.of(owned.applied()))) {
                setName(player, owned.original().orElse(null));
            }
            names.remove(player.getUniqueId());
            return;
        }
        String applied = marker + original.orElse(player.getUsername());
        if (!current.equals(Optional.of(applied))) {
            setName(player, applied);
        }
        names.put(player.getUniqueId(), new NameOverride(original, applied));
    }

    private void setName(Player player, String name) throws ReflectiveOperationException {
        api.getClass().getMethod("setCustomPlayerName", Player.class, String.class).invoke(api, player, name);
    }

    private static Object call(Object object, String method) throws ReflectiveOperationException {
        return object.getClass().getMethod(method).invoke(object);
    }

    static java.util.List<net.enthusia.staff.domain.staff.VanishRecord> loadVanished(
            net.enthusia.staff.domain.ports.VanishStore store) {
        var records = store.active(MAX_VANISHED);
        // A full bounded page may be truncated; do not publish incomplete visibility.
        if (records.size() >= MAX_VANISHED) {
            throw new IllegalStateException("vanish presence limit reached");
        }
        return records;
    }

    private static StaffRank rank(Player player) {
        for (StaffRank rank : new StaffRank[]{StaffRank.FOUNDER, StaffRank.DEVELOPER,
                StaffRank.ADMIN, StaffRank.MOD, StaffRank.HELPER}) {
            if (player.hasPermission("enthusiastaff.rank." + rank.name().toLowerCase(java.util.Locale.ROOT))) {
                return rank;
            }
        }
        return null;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (task != null) {
            task.cancel();
        }
        try {
            for (Player player : proxy.getAllPlayers()) {
                updateName(player, "");
            }
            if (call(api, "getVanishIntegration") == integration) {
                api.getClass().getMethod("setVanishIntegration", integrationType).invoke(api, previous);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warn("Staff tab integration cleanup unavailable ({})", exception.getClass().getSimpleName());
        }
    }

    private record NameOverride(Optional<String> original, String applied) {
    }
}

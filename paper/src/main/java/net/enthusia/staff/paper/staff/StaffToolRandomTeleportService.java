package net.enthusia.staff.paper.staff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Executes scheduler-safe random staff teleport while preserving dispatcher authorization boundaries. */
final class StaffToolRandomTeleportService {
    private static final String RANDOM_EXEMPT_PERMISSION = "enthusiastaff.stafftools.random-exempt";

    private final net.enthusia.staff.domain.investigation.PatrolHistory history;
    private final java.util.concurrent.ConcurrentHashMap<UUID, PatrolAttempt> attempts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Platform platform;
    private final String serverId;
    private final Predicate<String> enabled;
    private final CandidateEligibility candidateEligibility;
    private final Predicate<Player> actorAuthorized;
    private final Consumer<List<UUID>> shuffle;

    StaffToolRandomTeleportService(
            JavaPlugin plugin,
            String serverId,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze,
            StaffToolSettings settings
    ) {
        this(
                new BukkitPlatform(plugin),
                serverId,
                settings::randomTeleportEnabledOn,
                candidateFilter(plugin, staffMode, vanish, freeze, settings),
                actor -> staffMode.authorizedForTool(actor, StaffToolDefinition.RANDOM_TELEPORT)
                        && actor.hasPermission(StaffToolDefinition.RANDOM_TELEPORT.permission()),
                Collections::shuffle,
                plugin.getConfig().getInt("staff-tools.random-teleport.recent-targets", 20)
        );
    }

    StaffToolRandomTeleportService(
            Platform platform,
            String serverId,
            Predicate<String> enabled,
            CandidateEligibility candidateEligibility,
            Predicate<Player> actorAuthorized,
            Consumer<List<UUID>> shuffle
    ) {
        this(platform, serverId, enabled, candidateEligibility, actorAuthorized, shuffle, 20);
    }

    StaffToolRandomTeleportService(Platform platform, String serverId, Predicate<String> enabled,
            CandidateEligibility candidateEligibility, Predicate<Player> actorAuthorized,
            Consumer<List<UUID>> shuffle, int recentTargets) {
        this.history = new net.enthusia.staff.domain.investigation.PatrolHistory(10_000, recentTargets);
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.serverId = java.util.Objects.requireNonNull(serverId, "serverId");
        this.enabled = java.util.Objects.requireNonNull(enabled, "enabled");
        this.candidateEligibility = java.util.Objects.requireNonNull(candidateEligibility, "candidateEligibility");
        this.actorAuthorized = java.util.Objects.requireNonNull(actorAuthorized, "actorAuthorized");
        this.shuffle = java.util.Objects.requireNonNull(shuffle, "shuffle");
    }

    void begin(Player actor) {
        if (!enabled.test(serverId)) {
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Random staff teleport is disabled on backend " + serverId + '.',
                    NamedTextColor.YELLOW
            )));
            return;
        }
        PatrolAttempt attempt = new PatrolAttempt(actor.getUniqueId());
        if (attempts.size() >= 10_000 || attempts.putIfAbsent(attempt.actorId(), attempt) != null) {
            actor.sendMessage(StaffMessageStyle.style(Component.text("A patrol request is already pending; try again shortly.")));
            return;
        }
        try {
            platform.executeGlobal(() -> collectCandidates(attempt));
        } catch (RuntimeException failure) {
            attempts.remove(attempt.actorId(), attempt);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Random staff teleport could not start safely.",
                    NamedTextColor.RED
            )));
        }
    }

    private void collectCandidates(PatrolAttempt attempt) {
        if (!current(attempt)) { return; }
        final List<Player> candidates;
        try {
            candidates = List.copyOf(platform.onlinePlayers());
        } catch (RuntimeException failure) {
            message(attempt, "Random staff teleport could not inspect online players safely.");
            return;
        }
        if (candidates.isEmpty()) {
            message(attempt, "No suitable random-teleport target is online.");
            return;
        }
        ConcurrentLinkedQueue<UUID> eligible = new ConcurrentLinkedQueue<>();
        AtomicInteger remaining = new AtomicInteger(candidates.size());
        Runnable finishedOne = () -> finishCandidateCollection(attempt, eligible, remaining);
        for (Player candidate : candidates) {
            snapshotCandidate(attempt, candidate, eligible, finishedOne);
        }
    }

    private void finishCandidateCollection(
            PatrolAttempt attempt,
            Collection<UUID> eligible,
            AtomicInteger remaining
    ) {
        if (remaining.decrementAndGet() == 0) {
            finishTeleport(attempt, eligible);
        }
    }

    private void snapshotCandidate(
            PatrolAttempt attempt,
            Player target,
            Collection<UUID> eligible,
            Runnable finished
    ) {
        AtomicBoolean settled = new AtomicBoolean();
        Runnable retired = () -> settleCandidate(settled, finished);
        Runnable inspect = () -> inspectCandidate(attempt, target, eligible, settled, finished);
        try {
            boolean scheduled = platform.executeEntity(target, inspect, retired);
            if (!scheduled) {
                retired.run();
            }
        } catch (RuntimeException failure) {
            retired.run();
        }
    }

    private void inspectCandidate(
            PatrolAttempt attempt,
            Player target,
            Collection<UUID> eligible,
            AtomicBoolean settled,
            Runnable finished
    ) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        try {
            if (candidateEligibility.eligible(attempt.actorId(), target)) {
                eligible.add(target.getUniqueId());
            }
        } catch (RuntimeException ignored) {
            // Fail closed: this target simply does not enter the eligible queue.
        } finally {
            finished.run();
        }
    }

    private static void settleCandidate(AtomicBoolean settled, Runnable finished) {
        if (settled.compareAndSet(false, true)) {
            finished.run();
        }
    }

    private static CandidateEligibility candidateFilter(JavaPlugin plugin, StaffModeManager staffMode,
            VanishManager vanish, FreezeManager freeze, StaffToolSettings settings) {
        boolean skipIdle = plugin.getConfig().getBoolean("staff-tools.random-teleport.skip-idle", false);
        long seconds = plugin.getConfig().getLong("staff-tools.random-teleport.idle-seconds", 300L);
        if (seconds < 30 || seconds > 3600) { throw new IllegalArgumentException("patrol idle-seconds must be 30..3600"); }
        return (actor, target) -> {
            if (!eligibleCandidate(actor, target, staffMode, vanish, freeze, settings)) { return false; }
            if (!skipIdle) { return true; }
            var activity = plugin.getServer().getServicesManager().load(PlayerActivityListener.class);
            if (activity == null) { return true; }
            var latest = activity.tracker().snapshot(target.getUniqueId()).values().stream()
                    .max(java.time.Instant::compareTo);
            // Unknown activity remains eligible; absence of observations does not prove AFK.
            return latest.isEmpty() || latest.orElseThrow().isAfter(java.time.Instant.now().minusSeconds(seconds));
        };
    }

    private static boolean eligibleCandidate(
            UUID actorId,
            Player target,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze,
            StaffToolSettings settings
    ) {
        UUID targetId = target.getUniqueId();
        StaffToolTargetPolicy.Candidate candidate = new StaffToolTargetPolicy.Candidate(
                new StaffToolTargetPolicy.Identity(actorId, targetId),
                new StaffToolTargetPolicy.State(
                        staffMode.active(targetId),
                        vanish.isVanished(targetId),
                        freeze.isRestricted(targetId),
                        target.hasPermission(RANDOM_EXEMPT_PERMISSION),
                        target.isDead(),
                        target.isSleeping(),
                        target.isInsideVehicle()
                ),
                new StaffToolTargetPolicy.Environment(
                        target.getGameMode(),
                        settings.worldEnabled(target.getWorld().getName())
                )
        );
        return StaffToolTargetPolicy.eligibleRandomTarget(candidate);
    }

    private void finishTeleport(PatrolAttempt attempt, Collection<UUID> candidates) {
        List<UUID> shuffled = new ArrayList<>(candidates);
        shuffle.accept(shuffled);
        attemptNextCandidate(attempt, new ConcurrentLinkedQueue<>(history.preferFresh(attempt.actorId(), shuffled)));
    }

    private void attemptNextCandidate(PatrolAttempt attempt, ConcurrentLinkedQueue<UUID> candidates) {
        if (!current(attempt)) { return; }
        UUID targetId = candidates.poll();
        if (targetId == null) {
            message(attempt, "No suitable random-teleport target is online.");
            return;
        }
        onEntity(
                targetId,
                target -> revalidateCandidate(attempt, candidates, target),
                () -> attemptNextCandidate(attempt, candidates)
        );
    }

    private void revalidateCandidate(
            PatrolAttempt attempt,
            ConcurrentLinkedQueue<UUID> candidates,
            Player target
    ) {
        if (!current(attempt)) { return; }
        if (!candidateEligibility.eligible(attempt.actorId(), target)) {
            attemptNextCandidate(attempt, candidates);
            return;
        }
        TargetSnapshot snapshot = new TargetSnapshot(target.getUniqueId(), target.getName(), target.getLocation().clone());
        onEntity(attempt.actorId(), actor -> teleportToSnapshot(attempt, actor, snapshot),
                () -> attempts.remove(attempt.actorId(), attempt));
    }

    private void teleportToSnapshot(PatrolAttempt attempt, Player actor, TargetSnapshot target) {
        if (!current(attempt)) { return; }
        if (!canContinue(actor)) {
            attempts.remove(attempt.actorId(), attempt);
            return;
        }
        try {
            actor.teleportAsync(target.location()).whenComplete(
                    (success, failure) -> finishTeleport(attempt, target, success, failure)
            );
        } catch (RuntimeException failure) {
            finishTeleport(attempt, target, false, failure);
        }
    }

    private boolean canContinue(Player actor) {
        try {
            if (actorAuthorized.test(actor)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Authorization uncertainty fails closed.
        }
        actor.sendMessage(StaffMessageStyle.style(Component.text(
                "Random teleport was cancelled because your staff session or permission changed.",
                NamedTextColor.RED
        )));
        return false;
    }

    private void finishTeleport(PatrolAttempt attempt, TargetSnapshot target, Boolean success, Throwable failure) {
        if (failure != null || !Boolean.TRUE.equals(success)) {
            message(attempt, "Random staff teleport failed safely; no state was changed.");
            return;
        }
        onEntity(attempt.actorId(), actor -> {
            if (!current(attempt)) { return; }
            history.visited(attempt.actorId(), target.playerId());
            attempts.remove(attempt.actorId(), attempt);
            actor.sendMessage(StaffMessageStyle.style(Component.text(
                    "Teleported to a suitable random player: " + target.name() + '.')));
        }, () -> attempts.remove(attempt.actorId(), attempt));
    }

    private void onEntity(UUID playerId, Consumer<Player> operation) {
        onEntity(playerId, operation, () -> {
        });
    }

    private void onEntity(UUID playerId, Consumer<Player> operation, Runnable retired) {
        AtomicBoolean settled = new AtomicBoolean();
        Runnable retireOnce = () -> {
            if (settled.compareAndSet(false, true)) {
                retired.run();
            }
        };
        try {
            platform.executeGlobal(() -> resolvePlayer(playerId, operation, retired, settled, retireOnce));
        } catch (RuntimeException failure) {
            retireOnce.run();
        }
    }

    private void resolvePlayer(
            UUID playerId,
            Consumer<Player> operation,
            Runnable retired,
            AtomicBoolean settled,
            Runnable retireOnce
    ) {
        final Player player;
        try {
            player = platform.player(playerId);
        } catch (RuntimeException failure) {
            retireOnce.run();
            return;
        }
        if (player == null) {
            retireOnce.run();
            return;
        }
        Runnable owned = () -> runOwned(settled, player, operation, retired);
        try {
            boolean scheduled = platform.executeEntity(player, owned, retireOnce);
            if (!scheduled) {
                retireOnce.run();
            }
        } catch (RuntimeException failure) {
            retireOnce.run();
        }
    }

    private static void runOwned(
            AtomicBoolean settled,
            Player player,
            Consumer<Player> operation,
            Runnable retired
    ) {
        if (!settled.compareAndSet(false, true)) {
            return;
        }
        try {
            operation.accept(player);
        } catch (RuntimeException failure) {
            retired.run();
        }
    }

    private boolean current(PatrolAttempt attempt) { return attempts.get(attempt.actorId()) == attempt; }

    private void message(PatrolAttempt attempt, String text) {
        onEntity(attempt.actorId(), player -> {
            if (attempts.remove(attempt.actorId(), attempt)) {
                player.sendMessage(StaffMessageStyle.style(Component.text(text)));
            }
        }, () -> attempts.remove(attempt.actorId(), attempt));
    }

    @FunctionalInterface
    interface CandidateEligibility {
        boolean eligible(UUID actorId, Player target);
    }

    interface Platform {
        Collection<? extends Player> onlinePlayers();

        Player player(UUID playerId);

        void executeGlobal(Runnable operation);

        boolean executeEntity(Player player, Runnable operation, Runnable retired);
    }

    static final class BukkitPlatform implements Platform {
        private final Plugin plugin;

        BukkitPlatform(Plugin plugin) {
            this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        }

        @Override
        public Collection<? extends Player> onlinePlayers() {
            return plugin.getServer().getOnlinePlayers();
        }

        @Override
        public Player player(UUID playerId) {
            return plugin.getServer().getPlayer(playerId);
        }

        @Override
        public void executeGlobal(Runnable operation) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, operation);
        }

        @Override
        public boolean executeEntity(Player player, Runnable operation, Runnable retired) {
            return player.getScheduler().execute(plugin, operation, retired, 1L);
        }
    }

    void forget(UUID playerId) { history.forget(playerId); attempts.remove(playerId); }

    private static final class PatrolAttempt {
        private final UUID actorId;
        private PatrolAttempt(UUID actorId) { this.actorId = actorId; }
        private UUID actorId() { return actorId; }
    }

    private record TargetSnapshot(UUID playerId, String name, Location location) {
    }
}

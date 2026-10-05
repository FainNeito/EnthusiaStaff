package net.enthusia.staff.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.auth.StaffVanishVisibility;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.VanishStore;

/** Bounded async known-player lookup; never queries private storage on the command thread. */
final class VelocityPlayerSuggestions {
    private static final int MAX_VANISH_RECORDS = 10_000;
    private final ProxyServer proxy;
    private final Supplier<PlayerDirectory> directory;
    private final Supplier<VanishStore> vanish;
    private final Supplier<Executor> workers;
    private final Semaphore pending = new Semaphore(8);

    VelocityPlayerSuggestions(ProxyServer proxy, Supplier<PlayerDirectory> directory,
            Supplier<VanishStore> vanish, Supplier<Executor> workers) {
        this.proxy = proxy;
        this.directory = directory;
        this.vanish = vanish;
        this.workers = workers;
    }

    CompletableFuture<List<String>> suggest(CommandSource source, String prefix, String permission) {
        Executor executor = workers.get();
        if (!source.hasPermission(permission) || executor == null || !validPrefix(prefix) || !pending.tryAcquire()) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                completeRequest(source, prefix, permission, result);
            });
        } catch (RejectedExecutionException exception) {
            pending.release();
            result.complete(List.of());
        }
        return result.thenApply(names -> source.hasPermission(permission) ? names : List.of());
    }

    private void completeRequest(CommandSource source, String prefix, String permission,
            CompletableFuture<List<String>> result) {
        try {
            result.complete(source.hasPermission(permission) ? query(source, prefix) : List.of());
        } catch (RuntimeException exception) {
            // Provider unavailable: completion exposes neither identities nor private errors.
            result.complete(List.of());
        } finally {
            pending.release();
        }
    }

    private List<String> query(CommandSource source, String prefix) {
        PlayerDirectory players = directory.get();
        VanishStore hidden = vanish.get();
        if (players == null || hidden == null) { return List.of(); }
        var records = hidden.active(MAX_VANISH_RECORDS);
        if (records.size() >= MAX_VANISH_RECORDS) { return List.of(); }
        Map<UUID, StaffRank> vanished = new HashMap<>();
        records.forEach(record -> vanished.put(record.staffId(), record.rank()));
        Map<UUID, String> names = new HashMap<>();
        players.search(prefix, 50).forEach(identity -> identity.currentUsername()
                .ifPresent(name -> names.put(identity.playerId(), name)));
        proxy.getAllPlayers().forEach(player -> names.put(player.getUniqueId(), player.getUsername()));
        StaffRank viewerRank = rank(source);
        UUID viewer = source instanceof Player player ? player.getUniqueId() : null;
        return names.entrySet().stream()
                .filter(entry -> canSee(viewer, viewerRank, entry.getKey(), vanished.get(entry.getKey())))
                .map(Map.Entry::getValue).filter(name -> name.toLowerCase(Locale.ROOT)
                        .startsWith(prefix.toLowerCase(Locale.ROOT)))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(50).toList();
    }

    private boolean canSee(UUID viewer, StaffRank viewerRank, UUID target, StaffRank hiddenRank) {
        if (viewer == null || viewer.equals(target) || hiddenRank == null) { return true; }
        StaffRank current = proxy.getPlayer(target).map(VelocityPlayerSuggestions::rank).orElse(hiddenRank);
        return StaffVanishVisibility.canSee(viewerRank, hiddenRank)
                && StaffVanishVisibility.canSee(viewerRank, current);
    }

    private static StaffRank rank(CommandSource source) {
        for (StaffRank rank : new StaffRank[]{StaffRank.FOUNDER, StaffRank.DEVELOPER,
                StaffRank.ADMIN, StaffRank.MOD, StaffRank.HELPER}) {
            if (source.hasPermission("enthusiastaff.rank." + rank.name().toLowerCase(Locale.ROOT))) { return rank; }
        }
        return null;
    }

    static boolean validPrefix(String prefix) {
        return prefix != null && prefix.matches("(?:[a-zA-Z0-9_]{0,32}|\\*[a-zA-Z0-9_]{0,31})");
    }

    static List<String> operations(String prefix, boolean reopen) {
        return List.of("link", "approve", "household", "notrelated", "unlink", "reopen").stream()
                .filter(value -> reopen || !value.equals("reopen"))
                .filter(value -> value.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }

    static boolean targetPosition(String[] arguments) {
        return (arguments.length == 2 || arguments.length == 3)
                && operations("", true).contains(arguments[0].toLowerCase(Locale.ROOT));
    }
}

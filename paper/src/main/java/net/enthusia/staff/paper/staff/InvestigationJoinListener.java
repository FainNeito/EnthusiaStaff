package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.domain.investigation.JoinAlertLimiter;
import net.enthusia.staff.domain.ports.InvestigationFlagStore;
import net.enthusia.staff.domain.ports.StaffNoteStore;
import net.enthusia.staff.paper.api.StaffVisibilityService;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.command.InvestigationCommand;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads only bounded summaries asynchronously and rechecks visibility/permission at delivery. */
public final class InvestigationJoinListener implements Listener, AutoCloseable {
    public static final String ALERTS = "enthusiastaff.investigation.join-alerts";
    public static final String NOTES = "enthusiastaff.investigation.notes.view";
    private final JavaPlugin plugin;
    private final Clock clock;
    private final ExecutorService workers;
    private final Supplier<InvestigationFlagStore> flags;
    private final Supplier<StaffNoteStore> notes;
    private final ConcurrentHashMap<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private final JoinAlertLimiter limiter = new JoinAlertLimiter(10_000);
    private final boolean enabled;
    private volatile boolean closed;

    public InvestigationJoinListener(JavaPlugin plugin, Clock clock, ExecutorService workers,
            Supplier<InvestigationFlagStore> flags, Supplier<StaffNoteStore> notes) {
        this.plugin = plugin;
        this.clock = clock;
        this.workers = workers;
        this.flags = flags;
        this.notes = notes;
        enabled = plugin.getConfig().getBoolean("investigation.join-alerts.enabled", false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || closed || sessions.size() >= 10_000) { return; }
        UUID target = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        UUID token = UUID.randomUUID();
        sessions.put(target, token);
        try {
            workers.execute(() -> load(target, name, token));
        } catch (RejectedExecutionException ignored) {
            // Alerts are best effort. Never block joining or use a game thread as a database fallback.
        }
    }

    private void load(UUID target, String name, UUID token) {
        if (!sessionActive(target, token)) { return; }
        try {
            JoinSummary summary = summary(target, name, token);
            if (summary == null || summary.empty()) { return; }
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> dispatch(summary));
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Investigation join summary unavailable; no alert delivered.");
        }
    }

    private JoinSummary summary(UUID target, String name, UUID token) {
        var store = flags.get();
        var noteStore = notes.get();
        if (store == null || noteStore == null) { return null; }
        var cutoff = clock.instant().minus(Duration.ofDays(30));
        int active = store.active(target, clock.instant(), 20).size();
        long recent = noteStore.recent(target, 20).stream().filter(note -> note.createdAt().isAfter(cutoff)).count();
        return new JoinSummary(target, name, token, active, recent);
    }

    private void dispatch(JoinSummary summary) {
        if (!sessionActive(summary.target(), summary.token())) { return; }
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            viewer.getScheduler().execute(plugin, () -> deliver(viewer, summary), null, 1L);
        }
    }

    private boolean sessionActive(UUID target, UUID token) { return !closed && token.equals(sessions.get(target)); }

    private static boolean authorized(Player viewer) {
        return viewer.hasPermission(ALERTS) && viewer.hasPermission("enthusiastaff.inspect")
                && PaperActorResolver.resolve(viewer).isPresent();
    }

    private boolean visible(Player viewer, UUID target) {
        var visibility = plugin.getServer().getServicesManager().load(StaffVisibilityService.class);
        return visibility != null && visibility.canSee(viewer.getUniqueId(), target);
    }

    private boolean eligible(Player viewer, JoinSummary summary) {
        return sessionActive(summary.target(), summary.token()) && authorized(viewer) && visible(viewer, summary.target());
    }

    private void deliver(Player viewer, JoinSummary summary) {
        if (!eligible(viewer, summary)) { return; }
        int visibleFlags = viewer.hasPermission(InvestigationCommand.VIEW) ? summary.flags() : 0;
        long visibleNotes = viewer.hasPermission(NOTES) ? summary.notes() : 0;
        if (visibleFlags == 0 && visibleNotes == 0) { return; }
        if (!limiter.acquire(viewer.getUniqueId(), summary.target(), clock.instant())) { return; }
        Component message = Component.text(summary.name() + " joined: " + visibleFlags + " active flags, "
                + visibleNotes + " recent notes (up to 20 each). [Inspect]")
                .clickEvent(ClickEvent.runCommand("/inspect " + summary.target()));
        viewer.sendMessage(StaffMessageStyle.style(message));
    }

    private record JoinSummary(UUID target, String name, UUID token, int flags, long notes) {
        boolean empty() { return flags == 0 && notes == 0; }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() { closed = true; sessions.clear(); limiter.clear(); }
}

package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.investigation.InvestigationFlag;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.InvestigationFlagStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Explicit in-game flag operations; never a punishment or command execution bypass. */
public final class InvestigationCommand implements CommandExecutor {
    public static final String VIEW = "enthusiastaff.investigation.view";
    public static final String EDIT = "enthusiastaff.investigation.edit";
    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<InvestigationFlagStore> flags;
    private final Supplier<net.enthusia.staff.domain.ports.StaffNoteStore> notes;
    private final Supplier<PlayerDirectory> directory;
    private final Supplier<CaseLookup> cases;
    private final Supplier<OperationalMode> mode;
    private final ExecutorService workers;
    private final Set<String> categories;

    public InvestigationCommand(JavaPlugin plugin, Clock clock, Supplier<InvestigationFlagStore> flags,
            Supplier<PlayerDirectory> directory, Supplier<CaseLookup> cases, Supplier<OperationalMode> mode,
            ExecutorService workers, Supplier<net.enthusia.staff.domain.ports.StaffNoteStore> notes) {
        this.plugin = plugin;
        this.clock = clock;
        this.flags = flags;
        this.directory = directory;
        this.cases = cases;
        this.mode = mode;
        this.workers = workers;
        this.notes = notes;
        categories = plugin.getConfig().contains("investigation.flags.categories")
                ? Set.copyOf(plugin.getConfig().getStringList("investigation.flags.categories"))
                : Set.of("watch", "suspected-cheating", "behavior", "follow-up");
        if (categories.isEmpty() || categories.size() > 32
                || categories.stream().anyMatch(value -> !value.matches("[a-z][a-z0-9-]{0,31}"))) {
            throw new IllegalArgumentException("investigation flag categories require 1..32 lowercase IDs");
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player actor) || PaperActorResolver.resolve(sender).isEmpty()) {
            sender.sendMessage(StaffMessageStyle.error("Investigation flags require an in-game explicit staff rank."));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            if (CommandPermissionGate.require(actor, VIEW, "You cannot view investigation flags.")) {
                submit(actor, () -> list(actor, args[1]));
            }
        } else if (args.length >= 6 && args[0].equalsIgnoreCase("add")) {
            if (canEdit(actor)) { add(actor, args); }
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("resolve")) {
            if (canEdit(actor)) { resolve(actor, args); }
        } else {
            sender.sendMessage(StaffMessageStyle.style("/staffflags list <player> | add <player> <category> "
                    + "<hours|permanent> <case-id|none> <reason> | resolve <flag-uuid> <reason>"));
        }
        return true;
    }

    private boolean canEdit(Player actor) {
        if (!CommandPermissionGate.require(actor, EDIT, "You cannot edit investigation flags.")) { return false; }
        if (mode.get() != OperationalMode.ACTIVE) {
            actor.sendMessage(StaffMessageStyle.error("Flag edits require ACTIVE operational mode."));
            return false;
        }
        return true;
    }

    private void list(Player actor, String input) {
        PlayerDirectory players = directory.get();
        InvestigationFlagStore store = flags.get();
        if (players == null || store == null) { message(actor, "Investigation storage is not ready."); return; }
        var target = players.find(input).orElseThrow(() -> new IllegalArgumentException("Player absent from directory."));
        show(actor, target.playerId());
    }

    public void show(Player actor, UUID targetId) {
        var store = flags.get();
        if (store == null) { return; }
        var result = store.active(targetId, clock.instant(), 20);
        var noteStore = notes.get();
        var recentNotes = noteStore == null ? java.util.List.<net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote>of()
                : noteStore.recent(targetId, 10);
        owned(actor, () -> {
            if (PaperActorResolver.resolve(actor).isEmpty()) { return; }
            if (actor.hasPermission(net.enthusia.staff.paper.staff.InvestigationJoinListener.NOTES)) {
                actor.sendMessage(StaffMessageStyle.style("Recent staff notes (up to 10): " + recentNotes.size()));
                recentNotes.forEach(note -> actor.sendMessage(StaffMessageStyle.style(
                        note.createdAt() + " | actor " + note.actorId() + " | " + note.noteText())));
            }
            if (!actor.hasPermission(VIEW)) { return; }
            actor.sendMessage(StaffMessageStyle.style("Active investigation flags (up to 20): " + result.size()));
            result.forEach(flag -> actor.sendMessage(StaffMessageStyle.style(flag.flagId() + " | " + flag.category()
                    + " | " + flag.reason() + " | actor " + flag.actorId() + " | created " + flag.createdAt()
                    + " | expiry " + (flag.expiresAt() == null ? "permanent" : flag.expiresAt())
                    + " | case " + (flag.caseId() == null ? "none" : flag.caseId()))));
        });
    }

    private void add(Player actor, String[] args) {
        try {
            String category = args[2];
            if (!categories.contains(category)) { throw new IllegalArgumentException("Unknown category. Available: " + categories); }
            Duration lifetime = args[3].equalsIgnoreCase("permanent") ? null : Duration.ofHours(Long.parseLong(args[3]));
            if (lifetime != null && (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(Duration.ofDays(365)) > 0)) {
                throw new IllegalArgumentException("Expiry hours must be 1..8760, or permanent.");
            }
            CaseId linkedCase = args[4].equalsIgnoreCase("none") ? null : new CaseId(args[4]);
            UUID actorId = actor.getUniqueId();
            String reason = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
            submit(actor, () -> {
                var players = directory.get();
                if (players == null || flags.get() == null) { message(actor, "Investigation storage is not ready."); return; }
                var target = players.find(args[1]).orElseThrow(() -> new IllegalArgumentException("Player absent from directory."));
                if (linkedCase != null) {
                    var lookup = cases.get();
                    if (lookup == null || !lookup.target(linkedCase).filter(target.playerId()::equals).isPresent()) {
                        throw new IllegalArgumentException("Case must exist and belong to this player.");
                    }
                }
                var now = clock.instant();
                var flag = new InvestigationFlag(UUID.randomUUID(), target.playerId(), actorId, category,
                        reason, now, lifetime == null ? null : now.plus(lifetime), linkedCase);
                owned(actor, () -> {
                    if (PaperActorResolver.resolve(actor).isEmpty() || !canEdit(actor)) { return; }
                    submit(actor, () -> {
                        if (mode.get() != OperationalMode.ACTIVE) { message(actor, "Flag edit cancelled: mode changed."); return; }
                        var store = flags.get();
                        if (store == null) { message(actor, "Investigation storage is not ready."); return; }
                        store.create(flag);
                        message(actor, "Created investigation flag " + flag.flagId());
                    });
                });
            });
        } catch (IllegalArgumentException failure) { actor.sendMessage(StaffMessageStyle.error(failure.getMessage())); }
    }

    private void resolve(Player actor, String[] args) {
        try {
            UUID flagId = UUID.fromString(args[1]);
            UUID actorId = actor.getUniqueId();
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            new InvestigationFlag(flagId, flagId, actorId, "resolution", reason, clock.instant(), null, null);
            owned(actor, () -> {
                if (PaperActorResolver.resolve(actor).isEmpty() || !canEdit(actor)) { return; }
                submit(actor, () -> {
                    if (mode.get() != OperationalMode.ACTIVE) { message(actor, "Flag edit cancelled: mode changed."); return; }
                    var store = flags.get();
                    if (store == null) { message(actor, "Investigation storage is not ready."); return; }
                    message(actor, store.resolve(flagId, actorId, reason, clock.instant())
                            ? "Investigation flag resolved with audit." : "Flag absent or already resolved.");
                });
            });
        } catch (IllegalArgumentException failure) { actor.sendMessage(StaffMessageStyle.error(failure.getMessage())); }
    }

    private void submit(Player actor, Runnable operation) {
        try {
            workers.execute(() -> {
                try { operation.run(); }
                catch (IllegalArgumentException failure) { message(actor, failure.getMessage()); }
                catch (RuntimeException failure) {
                    plugin.getLogger().log(Level.WARNING, "Investigation storage operation failed", failure);
                    message(actor, "Investigation storage operation failed; no success is claimed.");
                }
            });
        } catch (RejectedExecutionException failure) { message(actor, "Investigation workers are busy; try again."); }
    }
    private void message(Player actor, String text) { owned(actor, () -> actor.sendMessage(StaffMessageStyle.style(text))); }
    private void owned(Player actor, Runnable operation) { actor.getScheduler().execute(plugin, operation, null, 1L); }
}

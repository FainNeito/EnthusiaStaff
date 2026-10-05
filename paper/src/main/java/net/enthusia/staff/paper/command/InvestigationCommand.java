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
    private static final int LIST_ARGUMENTS = 2;
    private static final int MINIMUM_ADD_ARGUMENTS = 6;
    private static final int MINIMUM_RESOLVE_ARGUMENTS = 3;
    private static final String STORAGE_NOT_READY = "Investigation storage is not ready.";
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
        if (args.length == 0) { usage(actor); return true; }
        route(actor, args);
        return true;
    }

    private void route(Player actor, String[] args) {
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "list" -> listRoute(actor, args);
            case "add" -> addRoute(actor, args);
            case "resolve" -> resolveRoute(actor, args);
            default -> usage(actor);
        }
    }

    private void listRoute(Player actor, String[] args) {
        if (args.length != LIST_ARGUMENTS) { usage(actor); return; }
        if (CommandPermissionGate.require(actor, VIEW, "You cannot view investigation flags.")) {
            submit(actor, () -> list(actor, args[1]));
        }
    }
    private void addRoute(Player actor, String[] args) {
        if (args.length < MINIMUM_ADD_ARGUMENTS) { usage(actor); return; }
        if (canEdit(actor)) { add(actor, args); }
    }
    private void resolveRoute(Player actor, String[] args) {
        if (args.length < MINIMUM_RESOLVE_ARGUMENTS) { usage(actor); return; }
        if (canEdit(actor)) { resolve(actor, args); }
    }
    private static void usage(Player actor) {
        actor.sendMessage(StaffMessageStyle.style("/staffflags list <player> | add <player> <category> "
                + "<hours|permanent> <case-id|none> <reason> | resolve <flag-uuid> <reason>"));
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
        if (players == null || store == null) { message(actor, STORAGE_NOT_READY); return; }
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
            result.forEach(flag -> renderFlag(actor, flag));
        });
    }

    private static void renderFlag(Player actor, InvestigationFlag flag) {
        actor.sendMessage(StaffMessageStyle.style(flag.flagId() + " | " + flag.category()
                + " | " + flag.reason() + " | actor " + flag.actorId() + " | created " + flag.createdAt()
                + " | expiry " + (flag.expiresAt() == null ? "permanent" : flag.expiresAt())
                + " | case " + (flag.caseId() == null ? "none" : flag.caseId())));
    }

    private void add(Player actor, String[] args) {
        try {
            FlagInput input = parseInput(args);
            UUID actorId = actor.getUniqueId();
            submit(actor, () -> prepareCreate(actor, actorId, input));
        } catch (IllegalArgumentException failure) { actor.sendMessage(StaffMessageStyle.error(failure.getMessage())); }
    }

    private FlagInput parseInput(String[] args) {
        String category = args[2];
        if (!categories.contains(category)) { throw new IllegalArgumentException("Unknown category. Available: " + categories); }
        Duration lifetime = parseLifetime(args[3]);
        CaseId linkedCase = args[4].equalsIgnoreCase("none") ? null : new CaseId(args[4]);
        String reason = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        return new FlagInput(args[1], category, reason, lifetime, linkedCase);
    }

    static Duration parseLifetime(String input) {
        if (input.equalsIgnoreCase("permanent")) { return null; }
        long hours = Long.parseLong(input);
        if (hours < 1 || hours > 8760) {
            throw new IllegalArgumentException("Expiry hours must be 1..8760, or permanent.");
        }
        return Duration.ofHours(hours);
    }

    private void prepareCreate(Player actor, UUID actorId, FlagInput input) {
        var players = directory.get();
        if (players == null || flags.get() == null) { message(actor, STORAGE_NOT_READY); return; }
        var target = players.find(input.target()).orElseThrow(() -> new IllegalArgumentException("Player absent from directory."));
        validateCase(input.caseId(), target.playerId());
        var now = clock.instant();
        var flag = new InvestigationFlag(UUID.randomUUID(), target.playerId(), actorId, input.category(), input.reason(),
                now, input.lifetime() == null ? null : now.plus(input.lifetime()), input.caseId());
        owned(actor, () -> authorizeCreate(actor, flag));
    }

    private void validateCase(CaseId linkedCase, UUID targetId) {
        if (linkedCase == null) { return; }
        var lookup = cases.get();
        if (lookup == null || lookup.target(linkedCase).filter(targetId::equals).isEmpty()) {
            throw new IllegalArgumentException("Case must exist and belong to this player.");
        }
    }

    private void authorizeCreate(Player actor, InvestigationFlag flag) {
        if (PaperActorResolver.resolve(actor).isEmpty() || !canEdit(actor)) { return; }
        submit(actor, () -> commitCreate(actor, flag));
    }

    private void commitCreate(Player actor, InvestigationFlag flag) {
        if (mode.get() != OperationalMode.ACTIVE) { message(actor, "Flag edit cancelled: mode changed."); return; }
        var store = flags.get();
        if (store == null) { message(actor, STORAGE_NOT_READY); return; }
        store.create(flag);
        message(actor, "Created investigation flag " + flag.flagId());
    }

    private record FlagInput(String target, String category, String reason, Duration lifetime, CaseId caseId) { }

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
                    if (store == null) { message(actor, STORAGE_NOT_READY); return; }
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

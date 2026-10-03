package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

/**
 * Console-only API for automated systems (Polar anticheat, etc.) to issue punishments
 * through the central {@link PunishmentService}.
 *
 * <p><b>SECURITY: This command is console-only.</b> No player — including staff of any
 * rank — can execute it. The check is hardcoded ({@code sender instanceof ConsoleCommandSender}),
 * not permission-based, so it cannot be bypassed by granting a permission node. This prevents
 * players from issuing fake "Polar" punishments.
 *
 * <p>Unlike {@code /punish} (a GUI workflow for staff), this command requires no player
 * interaction and works from console. Every punishment goes through the full pipeline:
 * database logging, Discord webhook, and commit effects (kick/ban enforcement).
 *
 * <p>Usage: {@code /staffapi punish <player> <ban|kick|mute|warn> [reason...] [--checks=<detail>]}
 *
 * <p><b>Polar placeholder limitation:</b> Polar only supports {@code {player}} and
 * {@code {server}} placeholders in punishment commands. There is no {@code {check}} or
 * {@code {flags}} placeholder, so {@code --checks} will usually be empty unless Polar
 * adds check-detail placeholders in a future version.
 */
public final class StaffApiCommand implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = Logger.getLogger(StaffApiCommand.class.getName());

    private static final String PUNISH_OPERATION = "punish";

    /** Reason ID for Polar anticheat detections (escalating ban ladder). */
    private static final String POLAR_REASON_ID = "cheating.polar.template";

    /** Actor used for all automated-system punishments. */
    private static final Actor SYSTEM_ACTOR = new Actor(
            new UUID(0L, 2L),
            "Polar Anticheat",
            StaffRank.SYSTEM
    );

    private static final Duration DEFAULT_MUTE_DURATION = Duration.ofDays(1);

    private final Supplier<PunishmentService> punishments;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<OperationalMode> mode;
    private final Clock clock;

    public StaffApiCommand(
            Supplier<PunishmentService> punishments,
            Supplier<PlayerDirectory> players,
            Supplier<OperationalMode> mode,
            Clock clock
    ) {
        this.punishments = Objects.requireNonNull(punishments, "punishments");
        this.players = Objects.requireNonNull(players, "players");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String operation = args[0].toLowerCase(Locale.ROOT);
        if (!PUNISH_OPERATION.equals(operation)) {
            sendUsage(sender, label);
            return true;
        }
        // SECURITY: Console-only. Hardcoded check — no permission node, cannot be granted to players.
        // This prevents fake "Polar" punishments from player-executed commands.
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("This command can only be run from the server console.");
            LOGGER.warning("[StaffAPI] Blocked non-console sender '" + sender.getName()
                    + "' attempting to use /staffapi punish");
            return true;
        }
        return onPunish(sender, label, args);
    }

    private boolean onPunish(CommandSender sender, String label, String[] args) {
        // punish <player> <type> [reason...] [--checks=<detail>]
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label + " punish <player> <ban|kick|mute|warn> [reason...] [--checks=<detail>]");
            return true;
        }
        String targetName = args[1];
        String typeName = args[2].toLowerCase(Locale.ROOT);

        SanctionType sanctionType = parseSanctionType(typeName);
        if (sanctionType == null) {
            sender.sendMessage("Unknown punishment type '" + args[2] + "'. Use: ban, kick, mute, warn.");
            return true;
        }

        // Split remaining args into reason words and --checks flag
        List<String> reasonWords = new ArrayList<>();
        String checksDetail = null;
        for (int i = 3; i < args.length; i++) {
            if (args[i].startsWith("--checks=")) {
                checksDetail = args[i].substring("--checks=".length());
            } else if (args[i].startsWith("--checks") && i + 1 < args.length) {
                checksDetail = args[++i];
            } else {
                reasonWords.add(args[i]);
            }
        }
        String reason = reasonWords.isEmpty() ? "Cheating" : String.join(" ", reasonWords);

        // Resolve target
        PlayerDirectory directory = players.get();
        if (directory == null) {
            sender.sendMessage("Player directory is not ready; punishment not issued.");
            return true;
        }
        Optional<PlayerIdentity> identity = directory.find(targetName);
        if (identity.isEmpty()) {
            sender.sendMessage("Player '" + targetName + "' not found; punishment not issued.");
            return true;
        }
        UUID targetId = identity.get().playerId();

        PunishmentService service = punishments.get();
        OperationalMode currentMode = mode.get();
        if (service == null || currentMode == null) {
            sender.sendMessage("Punishment service is not ready; punishment not issued.");
            return true;
        }

        // Build the request
        String internalExplanation = buildExplanation(reason, checksDetail, sender.getName());
        List<SanctionSpec> overrides = buildOverrideSanctions(sanctionType);
        String reasonId = overrides.isEmpty() ? POLAR_REASON_ID : "cheating.polar.template";

        IdempotencyKey key = new IdempotencyKey(
                "staffapi:" + targetId + ":" + sanctionType.name().toLowerCase(Locale.ROOT)
                        + ":" + clock.instant().toEpochMilli()
        );
        CreatePunishmentRequest request = new CreatePunishmentRequest(
                key,
                targetId,
                SYSTEM_ACTOR,
                reasonId,
                internalExplanation,
                CaseVisibility.PUBLIC,
                overrides
        );

        try {
            PunishmentResult result = service.create(request, currentMode);
            if (result instanceof PunishmentResult.Accepted accepted) {
                String message = "Punishment issued: " + sanctionType.name().toLowerCase(Locale.ROOT)
                        + " for " + targetName + " (case " + accepted.caseId() + ")";
                sender.sendMessage(message);
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("[StaffAPI] " + message + " by " + sender.getName());
                }
            } else if (result instanceof PunishmentResult.Rejected rejected) {
                String message = "Punishment rejected: " + rejected.code() + " - " + rejected.message();
                sender.sendMessage(message);
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("[StaffAPI] " + message + " (target=" + targetName + ")");
                }
            } else {
                sender.sendMessage("Unexpected punishment result; check logs.");
            }
        } catch (RuntimeException exception) {
            sender.sendMessage("Punishment failed with an error; check logs.");
            LOGGER.log(Level.SEVERE, "[StaffAPI] Punishment failed for " + targetName, exception);
        }
        return true;
    }

    private SanctionType parseSanctionType(String typeName) {
        return switch (typeName) {
            case "ban" -> SanctionType.NETWORK_BAN;
            case "kick" -> SanctionType.KICK;
            case "mute" -> SanctionType.MUTE;
            case "warn", "warning" -> SanctionType.WARNING;
            default -> null;
        };
    }

    /**
     * Builds override sanctions for one-shot types (kick/warn) or short mutes.
     * Bans use the reason ladder (empty overrides) so escalation applies.
     */
    private List<SanctionSpec> buildOverrideSanctions(SanctionType type) {
        return switch (type) {
            case KICK -> List.of(new SanctionSpec(SanctionType.KICK, SanctionLength.instant()));
            case WARNING -> List.of(new SanctionSpec(SanctionType.WARNING, SanctionLength.instant()));
            case MUTE -> List.of(new SanctionSpec(
                    SanctionType.MUTE, SanctionLength.temporary(DEFAULT_MUTE_DURATION)));
            default -> List.of(); // ban types use the reason ladder
        };
    }

    private String buildExplanation(String reason, String checksDetail, String issuer) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("Automated punishment via StaffAPI");
        if (issuer != null && !issuer.isBlank()) {
            explanation.append(" (triggered by ").append(issuer).append(")");
        }
        explanation.append(". Reason: ").append(reason);
        if (checksDetail != null && !checksDetail.isBlank()) {
            explanation.append(". Anticheat checks: ").append(checksDetail);
        }
        return explanation.toString();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " punish <player> <ban|kick|mute|warn> [reason...] [--checks=<detail>]");
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return List.of(PUNISH_OPERATION);
        }
        if (args.length == 3 && PUNISH_OPERATION.equalsIgnoreCase(args[0])) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            List<String> types = List.of("ban", "kick", "mute", "warn");
            List<String> matches = new ArrayList<>();
            for (String type : types) {
                if (type.startsWith(prefix)) {
                    matches.add(type);
                }
            }
            return matches;
        }
        return List.of();
    }
}

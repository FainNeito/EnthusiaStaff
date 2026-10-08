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
    private static final String CHECKS_FLAG = "--checks=";
    private static final String CHECKS_PREFIX = "--checks";
    private static final String DEFAULT_REASON = "Cheating";

    /** Minimum args for punish: operation, player, type. */
    private static final int MIN_PUNISH_ARGS = 3;
    /** Arg index for operation name. */
    private static final int ARG_OPERATION = 0;
    /** Arg index for target player name. */
    private static final int ARG_TARGET = 1;
    /** Arg index for punishment type. */
    private static final int ARG_TYPE = 2;
    /** Arg index where reason/flags start. */
    private static final int ARG_REASON_START = 3;
    /** Tab-completion: single arg (operation). */
    private static final int TAB_SINGLE_ARG = 1;
    /** Tab-completion: three args (operation, player, type). */
    private static final int TAB_TYPE_ARG = 3;

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
        String operation = args[ARG_OPERATION].toLowerCase(Locale.ROOT);
        if (!PUNISH_OPERATION.equals(operation)) {
            sendUsage(sender, label);
            return true;
        }
        // SECURITY: Console-only. Hardcoded check — no permission node, cannot be granted to players.
        // This prevents fake "Polar" punishments from player-executed commands.
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("This command can only be run from the server console.");
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("[StaffAPI] Blocked non-console sender '" + sender.getName()
                        + "' attempting to use /staffapi punish");
            }
            return true;
        }
        return onPunish(sender, label, args);
    }

    private boolean onPunish(CommandSender sender, String label, String[] args) {
        // punish <player> <type> [reason...] [--checks=<detail>]
        if (args.length < MIN_PUNISH_ARGS) {
            sendUsage(sender, label);
            return true;
        }
        PunishArgs parsed = parsePunishArgs(args);
        if (parsed == null) {
            sender.sendMessage("Unknown punishment type '" + args[ARG_TYPE]
                    + "'. Use: ban, kick, mute, warn.");
            return true;
        }
        return executePunishment(sender, parsed);
    }

    /** Parsed and validated punish arguments. Null if the type is unknown. */
    private PunishArgs parsePunishArgs(String[] args) {
        String targetName = args[ARG_TARGET];
        SanctionType sanctionType = parseSanctionType(args[ARG_TYPE].toLowerCase(Locale.ROOT));
        if (sanctionType == null) {
            return null;
        }
        ParsedReason parsedReason = parseReasonAndChecks(args);
        return new PunishArgs(targetName, sanctionType, parsedReason.reason(), parsedReason.checksDetail());
    }

    /** Splits trailing args into reason words and the --checks flag value. */
    private ParsedReason parseReasonAndChecks(String[] args) {
        List<String> reasonWords = new ArrayList<>();
        String checksDetail = null;
        for (int i = ARG_REASON_START; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith(CHECKS_FLAG)) {
                checksDetail = arg.substring(CHECKS_FLAG.length());
            } else if (arg.startsWith(CHECKS_PREFIX) && i + 1 < args.length) {
                checksDetail = args[++i];
            } else {
                reasonWords.add(arg);
            }
        }
        String reason = reasonWords.isEmpty() ? DEFAULT_REASON : String.join(" ", reasonWords);
        return new ParsedReason(reason, checksDetail);
    }

    private boolean executePunishment(CommandSender sender, PunishArgs parsed) {
        Optional<UUID> targetId = resolveTargetId(sender, parsed.targetName());
        if (targetId.isEmpty()) {
            return true;
        }
        PunishmentService service = punishments.get();
        OperationalMode currentMode = mode.get();
        if (service == null || currentMode == null) {
            sender.sendMessage("Punishment service is not ready; punishment not issued.");
            return true;
        }
        CreatePunishmentRequest request = buildRequest(sender, parsed, targetId.get());
        return submitPunishment(sender, service, currentMode, parsed, request);
    }

    private Optional<UUID> resolveTargetId(CommandSender sender, String targetName) {
        PlayerDirectory directory = players.get();
        if (directory == null) {
            sender.sendMessage("Player directory is not ready; punishment not issued.");
            return Optional.empty();
        }
        Optional<PlayerIdentity> identity = directory.find(targetName);
        if (identity.isEmpty()) {
            sender.sendMessage("Player '" + targetName + "' not found; punishment not issued.");
            return Optional.empty();
        }
        return Optional.of(identity.get().playerId());
    }

    private CreatePunishmentRequest buildRequest(
            CommandSender sender, PunishArgs parsed, UUID targetId) {
        String internalExplanation =
                buildExplanation(parsed.reason(), parsed.checksDetail(), sender.getName());
        List<SanctionSpec> overrides = buildOverrideSanctions(parsed.sanctionType());
        IdempotencyKey key = new IdempotencyKey(
                "staffapi:" + targetId + ":" + parsed.sanctionType().name().toLowerCase(Locale.ROOT)
                        + ":" + clock.instant().toEpochMilli()
        );
        return new CreatePunishmentRequest(
                key,
                targetId,
                SYSTEM_ACTOR,
                POLAR_REASON_ID,
                internalExplanation,
                CaseVisibility.PUBLIC,
                overrides
        );
    }

    private boolean submitPunishment(
            CommandSender sender,
            PunishmentService service,
            OperationalMode currentMode,
            PunishArgs parsed,
            CreatePunishmentRequest request
    ) {
        try {
            PunishmentResult result = service.create(request, currentMode);
            handlePunishmentResult(sender, parsed, result);
        } catch (RuntimeException exception) {
            sender.sendMessage("Punishment failed with an error; check logs.");
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE,
                        "[StaffAPI] Punishment failed for " + parsed.targetName(), exception);
            }
        }
        return true;
    }

    private void handlePunishmentResult(
            CommandSender sender, PunishArgs parsed, PunishmentResult result) {
        if (result instanceof PunishmentResult.Accepted accepted) {
            String message = "Punishment issued: "
                    + parsed.sanctionType().name().toLowerCase(Locale.ROOT)
                    + " for " + parsed.targetName() + " (case " + accepted.caseId() + ")";
            sender.sendMessage(message);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("[StaffAPI] " + message + " by " + sender.getName());
            }
        } else if (result instanceof PunishmentResult.Rejected rejected) {
            String message = "Punishment rejected: " + rejected.code() + " - " + rejected.message();
            sender.sendMessage(message);
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("[StaffAPI] " + message + " (target=" + parsed.targetName() + ")");
            }
        } else {
            sender.sendMessage("Unexpected punishment result; check logs.");
        }
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
        StringBuilder explanation = new StringBuilder(128);
        explanation.append("Automated punishment via StaffAPI");
        if (issuer != null && !issuer.isBlank()) {
            explanation.append(" (triggered by ").append(issuer).append(')');
        }
        explanation.append(". Reason: ").append(reason);
        if (checksDetail != null && !checksDetail.isBlank()) {
            explanation.append(". Anticheat checks: ").append(checksDetail);
        }
        return explanation.toString();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label
                + " punish <player> <ban|kick|mute|warn> [reason...] [--checks=<detail>]");
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == TAB_SINGLE_ARG) {
            return List.of(PUNISH_OPERATION);
        }
        if (args.length == TAB_TYPE_ARG && PUNISH_OPERATION.equalsIgnoreCase(args[ARG_OPERATION])) {
            return completePunishmentType(args[ARG_TYPE]);
        }
        return List.of();
    }

    private List<String> completePunishmentType(String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> types = List.of("ban", "kick", "mute", "warn");
        List<String> matches = new ArrayList<>();
        for (String type : types) {
            if (type.startsWith(lowerPrefix)) {
                matches.add(type);
            }
        }
        return matches;
    }

    /** Validated punish command arguments. */
    private record PunishArgs(String targetName, SanctionType sanctionType,
                              String reason, String checksDetail) {
    }

    /** Reason text plus optional anticheat check detail. */
    private record ParsedReason(String reason, String checksDetail) {
    }
}

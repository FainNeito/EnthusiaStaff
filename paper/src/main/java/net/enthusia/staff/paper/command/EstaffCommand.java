package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.paper.RuntimeHealth;
import net.enthusia.staff.paper.config.ConfigurationValidationAction;
import net.enthusia.staff.paper.config.ConfigurationValidationReport;
import net.enthusia.staff.paper.config.MessageCatalog;
import net.enthusia.staff.paper.config.MessageKey;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadResult;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class EstaffCommand implements CommandExecutor, TabCompleter {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(
            EstaffCommand.class.getName()
    );
    private static final String STATUS_PERMISSION = "enthusiastaff.status";
    private static final String VERIFY_PERMISSION = "enthusiastaff.verify";
    private static final String DIAGNOSTICS_PERMISSION = "enthusiastaff.diagnostics";
    private static final String RELOAD_PERMISSION = "enthusiastaff.reload";
    private static final String STATUS_OPERATION = "status";
    private static final String VERIFY_OPERATION = "verify";
    private static final String FULL_VERIFICATION_ARGUMENT = "full";
    private static final String RELOAD_OPERATION = "reload";
    private static final String CONFIG_OPERATION = "config";
    private static final String CONFIG_VALIDATE_ARGUMENT = "validate";
    private static final String SANCTION_OPERATION = "sanction";
    private static final String POLICY_V2_OPERATION = "policyv2";
    private static final String PUNISH_PERMISSION = "enthusiastaff.punish";
    private static final int NO_ARGUMENTS = 0;
    private static final int SINGLE_ARGUMENT = 1;
    private static final int FULL_VERIFICATION_ARGUMENTS = 2;
    private static final int MAX_RELOAD_DETAILS = 5;

    private final RuntimeHealth health;
    private final ConfigurationReloadAction reloadAction;
    private final ReloadDispatcher reloadDispatcher;
    private final JavaPlugin runtimePlugin;
    private final FullVerificationAction fallbackFullVerification;
    private final CopyOnWriteArrayList<Runnable> successfulReloadHooks = new CopyOnWriteArrayList<>();
    private volatile BooleanSupplier storagePublished = () -> false;
    private volatile SanctionLifecycleCommand sanctionLifecycle;
    private volatile PolicyV2ShadowAccess policyV2Shadow = PolicyV2ShadowAccess.disabled();
    private volatile ConfigurationValidationAction configurationValidation = () ->
            new ConfigurationValidationReport(
                    List.of(),
                    List.of("Versioned configuration validation is unavailable without the Paper runtime")
            );
    private volatile Supplier<MessageCatalog> messages = MessageCatalog::builtIn;

    public EstaffCommand(RuntimeHealth health) {
        this(
                health,
                () -> new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.APPLY_FAILED,
                        "EnthusiaStaff reload is unavailable",
                        List.of(),
                        false
                ),
                ReloadDispatcher.immediate(),
                null,
                () -> List.of(StaffMessageStyle.warning(
                        "Full verification is unavailable without the Paper runtime."
                ))
        );
    }

    public EstaffCommand(RuntimeHealth health, ConfigurationReloadAction reloadAction) {
        this(
                health,
                reloadAction,
                ReloadDispatcher.immediate(),
                null,
                () -> List.of(StaffMessageStyle.warning(
                        "Full verification is unavailable without the Paper runtime."
                ))
        );
    }

    public EstaffCommand(JavaPlugin plugin, RuntimeHealth health) {
        this(
                plugin,
                health,
                () -> new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.APPLY_FAILED,
                        "EnthusiaStaff reload is unavailable",
                        List.of(),
                        false
                )
        );
    }

    public EstaffCommand(
            JavaPlugin plugin,
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction
    ) {
        this(health, reloadAction, ReloadDispatcher.folia(plugin), plugin, List::of);
    }

    EstaffCommand(
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction,
            ReloadDispatcher reloadDispatcher
    ) {
        this(
                health,
                reloadAction,
                reloadDispatcher,
                null,
                () -> List.of(StaffMessageStyle.warning(
                        "Full verification is unavailable without the Paper runtime."
                ))
        );
    }

    EstaffCommand(
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction,
            ReloadDispatcher reloadDispatcher,
            FullVerificationAction fallbackFullVerification
    ) {
        this(health, reloadAction, reloadDispatcher, null, fallbackFullVerification);
    }

    private EstaffCommand(
            RuntimeHealth health,
            ConfigurationReloadAction reloadAction,
            ReloadDispatcher reloadDispatcher,
            JavaPlugin runtimePlugin,
            FullVerificationAction fallbackFullVerification
    ) {
        this.health = Objects.requireNonNull(health, "health");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.reloadDispatcher = Objects.requireNonNull(reloadDispatcher, "reloadDispatcher");
        this.runtimePlugin = runtimePlugin;
        this.fallbackFullVerification = Objects.requireNonNull(fallbackFullVerification, "fallbackFullVerification");
    }

    public void configureSanctionLifecycle(SanctionLifecycleCommand lifecycle) {
        sanctionLifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public void addSuccessfulReloadHook(Runnable hook) {
        successfulReloadHooks.add(Objects.requireNonNull(hook, "hook"));
    }

    public void configureStorageAvailability(BooleanSupplier storagePublished) {
        this.storagePublished = Objects.requireNonNull(storagePublished, "storagePublished");
    }

    public void configurePolicyV2Shadow(PolicyV2ShadowAccess access) {
        policyV2Shadow = Objects.requireNonNull(access, "access");
    }

    public void configureConfigurationValidation(ConfigurationValidationAction action) {
        configurationValidation = Objects.requireNonNull(action, "action");
    }

    public void configureMessages(Supplier<MessageCatalog> messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length > 0 && args[0].equalsIgnoreCase(CONFIG_OPERATION)) {
            return executeConfiguration(sender, label, args);
        }
        SanctionLifecycleCommand lifecycle = sanctionLifecycle;
        if (args.length > 0 && args[0].equalsIgnoreCase(SANCTION_OPERATION) && lifecycle != null) {
            return lifecycle.execute(sender, label, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase(POLICY_V2_OPERATION) && policyV2Shadow.enabled()) {
            return executePolicyV2(sender, label, args);
        }

        String operation = args.length == NO_ARGUMENTS ? STATUS_OPERATION : args[0].toLowerCase(Locale.ROOT);
        String permission = permissionFor(operation);
        if (permission == null) {
            if (requirePermission(
                    sender,
                    STATUS_PERMISSION,
                    message(MessageKey.ESTAFF_STATUS_PERMISSION_DENIED)
            )) {
                reportUsage(sender, label);
            }
            return true;
        }
        if (!requirePermission(sender, permission, denialMessage(operation))) {
            return true;
        }
        if (operation.equals(RELOAD_OPERATION)) {
            if (args.length != SINGLE_ARGUMENT) {
                reportUsage(sender, label);
                return true;
            }
            dispatchReload(sender);
            return true;
        }
        if (operation.equals(VERIFY_OPERATION)) {
            if (args.length == SINGLE_ARGUMENT) {
                reportStatus(sender);
                return true;
            }
            if (args.length == FULL_VERIFICATION_ARGUMENTS && args[1].equalsIgnoreCase(FULL_VERIFICATION_ARGUMENT)) {
                if (requirePermission(
                        sender,
                        DIAGNOSTICS_PERMISSION,
                        message(MessageKey.ESTAFF_DIAGNOSTICS_PERMISSION_DENIED)
                )) {
                    reportFullVerification(sender);
                }
                return true;
            }
            reportUsage(sender, label);
            return true;
        }
        if (args.length > SINGLE_ARGUMENT) {
            reportUsage(sender, label);
            return true;
        }
        reportStatus(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        SanctionLifecycleCommand lifecycle = sanctionLifecycle;
        if (args.length > 0 && args[0].equalsIgnoreCase(SANCTION_OPERATION) && lifecycle != null) {
            return lifecycle.complete(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase(POLICY_V2_OPERATION) && policyV2Shadow.enabled()) {
            return List.of();
        }
        if (args.length == FULL_VERIFICATION_ARGUMENTS && args[0].equalsIgnoreCase(VERIFY_OPERATION)) {
            if (allowedWithoutMessage(sender, VERIFY_PERMISSION)
                    && allowedWithoutMessage(sender, DIAGNOSTICS_PERMISSION)
                    && FULL_VERIFICATION_ARGUMENT.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                return List.of(FULL_VERIFICATION_ARGUMENT);
            }
            return List.of();
        }
        if (args.length == FULL_VERIFICATION_ARGUMENTS && args[0].equalsIgnoreCase(CONFIG_OPERATION)) {
            if (!allowedWithoutMessage(sender, RELOAD_PERMISSION)) {
                return List.of();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            if (CONFIG_VALIDATE_ARGUMENT.startsWith(prefix)) {
                matches.add(CONFIG_VALIDATE_ARGUMENT);
            }
            if (RELOAD_OPERATION.startsWith(prefix)) {
                matches.add(RELOAD_OPERATION);
            }
            return List.copyOf(matches);
        }
        if (args.length > SINGLE_ARGUMENT) {
            return List.of();
        }
        String prefix = args.length == NO_ARGUMENTS ? "" : args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        addCompletion(sender, matches, prefix, STATUS_OPERATION, STATUS_PERMISSION);
        addCompletion(sender, matches, prefix, VERIFY_OPERATION, VERIFY_PERMISSION);
        addCompletion(sender, matches, prefix, RELOAD_OPERATION, RELOAD_PERMISSION);
        addCompletion(sender, matches, prefix, CONFIG_OPERATION, RELOAD_PERMISSION);
        if (lifecycle != null && SANCTION_OPERATION.startsWith(prefix)
                && hasAnySanctionPermission(sender)) {
            matches.add(SANCTION_OPERATION);
        }
        if (policyV2Shadow.enabled() && POLICY_V2_OPERATION.startsWith(prefix)
                && allowedWithoutMessage(sender, PUNISH_PERMISSION)) {
            matches.add(POLICY_V2_OPERATION);
        }
        return List.copyOf(matches);
    }

    private boolean executeConfiguration(CommandSender sender, String label, String[] args) {
        if (!requirePermission(
                sender,
                RELOAD_PERMISSION,
                message(MessageKey.ESTAFF_CONFIG_PERMISSION_DENIED)
        )) {
            return true;
        }
        if (args.length != FULL_VERIFICATION_ARGUMENTS) {
            reportUsage(sender, label);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals(CONFIG_VALIDATE_ARGUMENT)) {
            reportConfigurationValidation(sender);
            return true;
        }
        if (action.equals(RELOAD_OPERATION)) {
            dispatchReload(sender);
            return true;
        }
        reportUsage(sender, label);
        return true;
    }

    private void reportConfigurationValidation(CommandSender sender) {
        ConfigurationValidationReport report;
        try {
            report = Objects.requireNonNull(
                    configurationValidation.validate(),
                    "configuration validation report"
            );
        } catch (RuntimeException exception) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(
                        Level.WARNING,
                        "Versioned configuration validation failed unexpectedly: "
                                + exception.getClass().getSimpleName()
                );
            }
            sender.sendMessage(StaffMessageStyle.error(
                    message(MessageKey.ESTAFF_CONFIG_VALIDATION_UNEXPECTED)
            ));
            return;
        }
        if (report.valid()) {
            sender.sendMessage(StaffMessageStyle.success(
                    message(MessageKey.ESTAFF_CONFIG_VALIDATION_PASSED)
            ));
            for (ConfigurationValidationReport.Entry entry : report.entries()) {
                sender.sendMessage(StaffMessageStyle.info(
                        entry.source() + " • " + entry.version()
                ));
            }
            return;
        }
        sender.sendMessage(StaffMessageStyle.error(
                message(MessageKey.ESTAFF_CONFIG_VALIDATION_FAILED)
        ));
        int shown = Math.min(report.errors().size(), MAX_RELOAD_DETAILS);
        for (int index = 0; index < shown; index++) {
            sender.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(report.errors().get(index), NamedTextColor.GRAY)));
        }
        if (report.errors().size() > shown) {
            sender.sendMessage(StaffMessageStyle.info(
                    message(MessageKey.ESTAFF_CONFIG_ERRORS_OMITTED)
            ));
        }
    }

    private boolean executePolicyV2(CommandSender sender, String label, String[] args) {
        if (!requirePermission(
                sender,
                PUNISH_PERMISSION,
                message(MessageKey.ESTAFF_POLICY_V2_PERMISSION_DENIED)
        )) {
            return true;
        }
        if (args.length != FULL_VERIFICATION_ARGUMENTS) {
            reportUsage(sender, label);
            return true;
        }
        policyV2Shadow.open(sender, args[1]);
        return true;
    }

    static boolean requirePermission(CommandSender sender, String permission, String denialMessage) {
        if (permission != null && !permission.isBlank() && sender instanceof ConsoleCommandSender) {
            return true;
        }
        return CommandPermissionGate.require(sender, permission, denialMessage);
    }

    private void dispatchReload(CommandSender sender) {
        ReloadDispatch dispatch = reloadDispatcher.dispatch(
                sender,
                reloadAction,
                result -> reportReload(sender, result)
        );
        if (dispatch == ReloadDispatch.SCHEDULED) {
            sender.sendMessage(StaffMessageStyle.warning(
                    message(MessageKey.ESTAFF_RELOAD_SCHEDULED)
            ));
        } else if (dispatch == ReloadDispatch.REJECTED) {
            sender.sendMessage(StaffMessageStyle.error(
                    message(MessageKey.ESTAFF_RELOAD_REJECTED)
            ));
        }
    }

    private void reportStatus(CommandSender sender) {
        RuntimeHealth.Snapshot snapshot = health.snapshot();
        sender.sendMessage(StaffMessageStyle.modeHeader(
                message(MessageKey.ESTAFF_STATUS_TITLE),
                snapshot.mode()
        ));
        if (snapshot.issues().isEmpty()) {
            sender.sendMessage(StaffMessageStyle.statusRow(
                    message(MessageKey.ESTAFF_STATUS_RUNTIME_LABEL),
                    message(MessageKey.ESTAFF_STATUS_HEALTHY),
                    message(MessageKey.ESTAFF_STATUS_NO_ISSUES),
                    StaffMessageStyle.Tone.SUCCESS
            ));
            return;
        }
        for (Map.Entry<String, String> issue : snapshot.issues().entrySet()) {
            StaffMessageStyle.Tone tone = StaffMessageStyle.issueTone(
                    issue.getKey(),
                    issue.getValue(),
                    snapshot.mode()
            );
            sender.sendMessage(StaffMessageStyle.statusRow(
                    humanLabel(issue.getKey()),
                    tone == StaffMessageStyle.Tone.ERROR
                            ? message(MessageKey.ESTAFF_STATUS_BLOCKED)
                            : message(MessageKey.ESTAFF_STATUS_DISABLED),
                    issue.getValue(),
                    tone
            ));
        }
    }

    private void reportFullVerification(CommandSender sender) {
        try {
            List<Component> messages = runtimePlugin == null
                    ? fallbackFullVerification.verify()
                    : new FullRuntimeVerifier(runtimePlugin, health, storagePublished).verify();
            messages.forEach(sender::sendMessage);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Full EnthusiaStaff verification failed", exception);
            sender.sendMessage(StaffMessageStyle.error(
                    message(MessageKey.ESTAFF_FULL_VERIFICATION_FAILED)
            ));
        }
    }

    private void reportUsage(CommandSender sender, String label) {
        String operations = policyV2Shadow.enabled()
                ? "status|verify [full]|reload|config <validate|reload>|sanction|policyv2 <player>"
                : "status|verify [full]|reload|config <validate|reload>|sanction";
        sender.sendMessage(StaffMessageStyle.usage(message(
                MessageKey.ESTAFF_USAGE,
                Map.of("label", label, "operations", operations)
        )));
    }

    private void reportReload(CommandSender sender, ConfigurationReloadResult result) {
        if (result.successful()) {
            runSuccessfulReloadHooks(sender);
        }
        sender.sendMessage(result.successful()
                ? StaffMessageStyle.success(result.message())
                : StaffMessageStyle.error(result.message()));
        int shown = Math.min(result.details().size(), MAX_RELOAD_DETAILS);
        for (int index = 0; index < shown; index++) {
            sender.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(result.details().get(index), NamedTextColor.GRAY)));
        }
        if (result.details().size() > shown) {
            sender.sendMessage(StaffMessageStyle.info(
                    message(MessageKey.ESTAFF_RELOAD_DETAILS_OMITTED)
            ));
        }
        if (result.reasonPoliciesReloaded()) {
            sender.sendMessage(StaffMessageStyle.success(message(MessageKey.ESTAFF_REASON_POLICIES_RELOADED)));
        }
    }

    private void runSuccessfulReloadHooks(CommandSender sender) {
        for (Runnable hook : successfulReloadHooks) {
            try {
                hook.run();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Successful reload hook failed", exception);
                sender.sendMessage(StaffMessageStyle.warning(
                        message(MessageKey.ESTAFF_PRESENTATION_HOOK_FAILED)
                ));
            }
        }
    }

    private static void addCompletion(
            CommandSender sender,
            List<String> completions,
            String prefix,
            String operation,
            String permission
    ) {
        if (operation.startsWith(prefix) && allowedWithoutMessage(sender, permission)) {
            completions.add(operation);
        }
    }

    private static boolean allowedWithoutMessage(CommandSender sender, String permission) {
        return sender instanceof ConsoleCommandSender || sender.hasPermission(permission);
    }

    private static boolean hasAnySanctionPermission(CommandSender sender) {
        return sender instanceof ConsoleCommandSender
                || sender.hasPermission(SanctionLifecycleCommand.REDUCE_PERMISSION)
                || sender.hasPermission(SanctionLifecycleCommand.END_PERMISSION)
                || sender.hasPermission(SanctionLifecycleCommand.REVOKE_PERMISSION)
                || sender.hasPermission(SanctionLifecycleCommand.OVERTURN_PERMISSION);
    }

    private static String permissionFor(String operation) {
        return switch (operation) {
            case STATUS_OPERATION -> STATUS_PERMISSION;
            case VERIFY_OPERATION -> VERIFY_PERMISSION;
            case RELOAD_OPERATION -> RELOAD_PERMISSION;
            default -> null;
        };
    }

    private String denialMessage(String operation) {
        return switch (operation) {
            case VERIFY_OPERATION -> message(MessageKey.ESTAFF_VERIFY_PERMISSION_DENIED);
            case RELOAD_OPERATION -> message(MessageKey.ESTAFF_RELOAD_PERMISSION_DENIED);
            default -> message(MessageKey.ESTAFF_STATUS_PERMISSION_DENIED);
        };
    }

    private String humanLabel(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', ' ').replace('_', ' ');
        if (normalized.isBlank()) {
            return message(MessageKey.ESTAFF_STATUS_RUNTIME_LABEL);
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String message(MessageKey key) {
        return Objects.requireNonNull(messages.get(), "message catalog").text(key);
    }

    private String message(MessageKey key, Map<String, ?> values) {
        return Objects.requireNonNull(messages.get(), "message catalog").render(key, values);
    }

    enum ReloadDispatch {
        COMPLETED,
        SCHEDULED,
        REJECTED
    }

    @FunctionalInterface
    interface ReloadDispatcher {
        ReloadDispatch dispatch(
                CommandSender sender,
                ConfigurationReloadAction action,
                Consumer<ConfigurationReloadResult> reporter
        );

        static ReloadDispatcher immediate() {
            return (sender, action, reporter) -> {
                reporter.accept(action.reload());
                return ReloadDispatch.COMPLETED;
            };
        }

        static ReloadDispatcher folia(JavaPlugin plugin) {
            Objects.requireNonNull(plugin, "plugin");
            return (sender, action, reporter) -> {
                if (!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) {
                    return ReloadDispatch.REJECTED;
                }
                try {
                    plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                        ConfigurationReloadResult result = safeReload(plugin, action);
                        if (sender instanceof Player player) {
                            dispatchPlayerResult(plugin, player, reporter, result);
                        } else {
                            reporter.accept(result);
                        }
                    });
                    return ReloadDispatch.SCHEDULED;
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(
                            Level.WARNING,
                            "EnthusiaStaff reload could not be scheduled on the global region thread",
                            exception
                    );
                    return ReloadDispatch.REJECTED;
                }
            };
        }

        private static ConfigurationReloadResult safeReload(
                JavaPlugin plugin,
                ConfigurationReloadAction action
        ) {
            try {
                return action.reload();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "EnthusiaStaff reload failed unexpectedly", exception);
                return new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.APPLY_FAILED,
                        "Reload failed unexpectedly; previous runtime state was retained where possible",
                        List.of("See the sanitized server log for the failure category"),
                        false
                );
            }
        }

        private static void dispatchPlayerResult(
                JavaPlugin plugin,
                Player player,
                Consumer<ConfigurationReloadResult> reporter,
                ConfigurationReloadResult result
        ) {
            try {
                boolean scheduled = player.getScheduler().execute(
                        plugin,
                        () -> reporter.accept(result),
                        () -> plugin.getLogger().fine(
                                "Reload result was not delivered because the command sender disconnected"
                        ),
                        1L
                );
                if (!scheduled) {
                    plugin.getLogger().fine(
                            "Reload result was not delivered because the command sender is no longer schedulable"
                    );
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                        Level.FINE,
                        "Reload result could not be returned to the command sender",
                        exception
                );
            }
        }
    }

    @FunctionalInterface
    interface FullVerificationAction {
        List<Component> verify();
    }
}

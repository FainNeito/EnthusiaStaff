package net.enthusia.staff.paper.config;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MessageKey {
    ESTAFF_STATUS_PERMISSION_DENIED(
            "estaff.permissions.status",
            "You do not have permission to view EnthusiaStaff status."
    ),
    ESTAFF_VERIFY_PERMISSION_DENIED(
            "estaff.permissions.verify",
            "You do not have permission to verify EnthusiaStaff runtime state."
    ),
    ESTAFF_RELOAD_PERMISSION_DENIED(
            "estaff.permissions.reload",
            "You do not have permission to reload EnthusiaStaff configuration."
    ),
    ESTAFF_DIAGNOSTICS_PERMISSION_DENIED(
            "estaff.permissions.diagnostics",
            "You do not have permission to run full EnthusiaStaff diagnostics."
    ),
    ESTAFF_CONFIG_PERMISSION_DENIED(
            "estaff.permissions.config",
            "You do not have permission to validate or reload EnthusiaStaff configuration."
    ),
    ESTAFF_POLICY_V2_PERMISSION_DENIED(
            "estaff.permissions.policy-v2",
            "You do not have permission to use Policy v2 shadow review."
    ),
    ESTAFF_CONFIG_VALIDATION_UNEXPECTED(
            "estaff.config.validation-unexpected",
            "Configuration validation failed unexpectedly; no runtime state was changed."
    ),
    ESTAFF_CONFIG_VALIDATION_PASSED(
            "estaff.config.validation-passed",
            "Configuration validation passed; no runtime state was changed."
    ),
    ESTAFF_CONFIG_VALIDATION_FAILED(
            "estaff.config.validation-failed",
            "Configuration validation failed; no runtime state was changed."
    ),
    ESTAFF_CONFIG_ERRORS_OMITTED(
            "estaff.config.errors-omitted",
            "Additional configuration errors were omitted from command output."
    ),
    ESTAFF_RELOAD_SCHEDULED(
            "estaff.reload.scheduled",
            "EnthusiaStaff reload scheduled on the global region thread."
    ),
    ESTAFF_RELOAD_REJECTED(
            "estaff.reload.rejected",
            "EnthusiaStaff reload could not be scheduled; no configuration was changed."
    ),
    ESTAFF_STATUS_TITLE(
            "estaff.status.title",
            "EnthusiaStaff"
    ),
    ESTAFF_STATUS_RUNTIME_LABEL(
            "estaff.status.runtime-label",
            "Runtime"
    ),
    ESTAFF_STATUS_HEALTHY(
            "estaff.status.healthy",
            "Healthy"
    ),
    ESTAFF_STATUS_NO_ISSUES(
            "estaff.status.no-issues",
            "No active runtime health issues"
    ),
    ESTAFF_STATUS_BLOCKED(
            "estaff.status.blocked",
            "Blocked"
    ),
    ESTAFF_STATUS_DISABLED(
            "estaff.status.disabled",
            "Disabled"
    ),
    ESTAFF_FULL_VERIFICATION_FAILED(
            "estaff.verify.full-failed",
            "Full verification failed; see the sanitized server log."
    ),
    ESTAFF_USAGE(
            "estaff.usage",
            "<gray>Usage: <aqua>/{label} {operations}</aqua></gray>",
            Set.of("label", "operations")
    ),
    ESTAFF_RELOAD_DETAILS_OMITTED(
            "estaff.reload.details-omitted",
            "Additional sanitized reload details were written to the server log."
    ),
    ESTAFF_REASON_POLICIES_RELOADED(
            "estaff.reload.reason-policies-reloaded",
            "Reason policies were replaced atomically."
    ),
    ESTAFF_PRESENTATION_HOOK_FAILED(
            "estaff.reload.presentation-hook-failed",
            "Reload applied, but a presentation-settings hook failed; previous values remain active."
    ),
    VANISH_PERMISSION_DENIED(
            "vanish.permission-denied",
            "You do not have permission to change vanish or spectator tab visibility.",
            Set.of(),
            2
    ),
    VANISH_PLAYER_ONLY(
            "vanish.player-only",
            "Only a player can change vanish or spectator tab visibility.",
            Set.of(),
            2
    ),
    VANISH_USAGE(
            "vanish.usage",
            "<gray>Usage: </gray><aqua>/{label} | /{label} tab {choices}</aqua>",
            Set.of("label", "choices"),
            2
    ),
    VANISH_MODE_DISABLED(
            "vanish.mode-disabled",
            "Vanish enable is disabled while moderation is {mode}.",
            Set.of("mode"),
            2
    );

    private static final Map<String, MessageKey> BY_PATH = java.util.Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(MessageKey::path, Function.identity()));

    private final String path;
    private final String defaultText;
    private final Set<String> placeholders;
    private final int introducedSchemaVersion;

    MessageKey(String path, String defaultText) {
        this(path, defaultText, Set.of(), 1);
    }

    MessageKey(String path, String defaultText, Set<String> placeholders) {
        this(path, defaultText, placeholders, 1);
    }

    MessageKey(String path, String defaultText, Set<String> placeholders, int introducedSchemaVersion) {
        this.path = path;
        this.defaultText = defaultText;
        this.placeholders = Set.copyOf(placeholders);
        this.introducedSchemaVersion = introducedSchemaVersion;
    }

    public String path() {
        return path;
    }

    public String defaultText() {
        return defaultText;
    }

    public Set<String> placeholders() {
        return placeholders;
    }

    public int introducedSchemaVersion() {
        return introducedSchemaVersion;
    }

    public static MessageKey fromPath(String path) {
        return BY_PATH.get(path);
    }
}

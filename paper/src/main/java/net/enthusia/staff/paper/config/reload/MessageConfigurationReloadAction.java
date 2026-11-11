package net.enthusia.staff.paper.config.reload;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.enthusia.staff.paper.config.AtomicMessageConfiguration;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.MessageConfigurationSnapshot;

public final class MessageConfigurationReloadAction implements ConfigurationReloadAction {
    private final ConfigurationReloadAction delegate;
    private final Supplier<MessageConfigurationSnapshot> candidates;
    private final AtomicMessageConfiguration active;
    private final Consumer<List<String>> rejectedCandidateLogger;

    public MessageConfigurationReloadAction(
            ConfigurationReloadAction delegate,
            Supplier<MessageConfigurationSnapshot> candidates,
            AtomicMessageConfiguration active,
            Consumer<List<String>> rejectedCandidateLogger
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.active = Objects.requireNonNull(active, "active");
        this.rejectedCandidateLogger = Objects.requireNonNull(
                rejectedCandidateLogger,
                "rejectedCandidateLogger"
        );
    }

    @Override
    public ConfigurationReloadResult reload() {
        synchronized (active) {
            return reloadLocked();
        }
    }

    private ConfigurationReloadResult reloadLocked() {
        MessageConfigurationSnapshot candidate;
        try {
            candidate = Objects.requireNonNull(candidates.get(), "message configuration candidate");
        } catch (ConfigurationValidationException exception) {
            return validationFailed(sanitized(exception.getMessage()));
        } catch (RuntimeException exception) {
            return validationFailed("Message configuration candidate could not be loaded");
        }

        MessageConfigurationSnapshot previous = active.snapshot();
        ConfigurationReloadResult delegated = delegate.reload();
        if (!delegated.successful() || previous.equals(candidate)) {
            return delegated;
        }
        if (!active.replace(previous, candidate)) {
            return new ConfigurationReloadResult(
                    ConfigurationReloadResult.Outcome.UNAVAILABLE,
                    "Message configuration changed concurrently; the validated candidate was not applied",
                    List.of("Retry reload after reviewing the active runtime state"),
                    delegated.reasonPoliciesReloaded()
            );
        }
        ConfigurationReloadResult.Outcome outcome = delegated.outcome()
                == ConfigurationReloadResult.Outcome.NO_CHANGES
                ? ConfigurationReloadResult.Outcome.APPLIED
                : delegated.outcome();
        String message = delegated.outcome() == ConfigurationReloadResult.Outcome.NO_CHANGES
                ? "Message configuration reloaded atomically; other settings were unchanged"
                : delegated.message() + "; message configuration was replaced atomically";
        return new ConfigurationReloadResult(
                outcome,
                message,
                delegated.details(),
                delegated.reasonPoliciesReloaded()
        );
    }

    private ConfigurationReloadResult validationFailed(String detail) {
        List<String> details = List.of(detail);
        rejectedCandidateLogger.accept(details);
        return new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.VALIDATION_FAILED,
                "Message configuration validation failed; previous messages remain active",
                details,
                false
        );
    }

    private static String sanitized(String message) {
        if (message == null || message.isBlank()) {
            return "messages.yml is invalid";
        }
        String firstLine = message.lines().findFirst().orElse("messages.yml is invalid").trim();
        return firstLine.length() <= 240 ? firstLine : firstLine.substring(0, 240);
    }
}

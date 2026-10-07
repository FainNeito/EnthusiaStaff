package net.enthusia.staff.paper.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.config.reload.MessageConfigurationReloadAction;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageConfigurationRuntime {
    private static final Object INITIALIZATION_LOCK = new Object();

    private static volatile AtomicMessageConfiguration active;

    private MessageConfigurationRuntime() {
    }

    public static ConfigurationReloadAction initialize(
            JavaPlugin plugin,
            ConfigurationReloadAction delegate
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(delegate, "delegate");

        AtomicMessageConfiguration runtime = active;
        if (runtime == null) {
            synchronized (INITIALIZATION_LOCK) {
                runtime = active;
                if (runtime == null) {
                    runtime = initializeActive(plugin);
                    active = runtime;
                }
            }
        }

        MessageConfigurationLoader loader = new MessageConfigurationLoader();
        return new MessageConfigurationReloadAction(
                delegate,
                () -> loader.load(file(plugin)),
                runtime,
                details -> {
                    plugin.getLogger().warning("EnthusiaStaff message configuration reload was rejected");
                    details.forEach(detail -> plugin.getLogger().warning("Reload detail: " + detail));
                }
        );
    }

    public static MessageConfigurationSnapshot snapshot() {
        AtomicMessageConfiguration runtime = active;
        if (runtime == null) {
            throw new IllegalStateException("message configuration runtime has not been initialized");
        }
        return runtime.snapshot();
    }

    public static MessageCatalog catalog() {
        return snapshot().catalog();
    }

    private static AtomicMessageConfiguration initializeActive(JavaPlugin plugin) {
        if (Files.notExists(file(plugin))) {
            plugin.saveResource("messages.yml", false);
        }
        MessageConfigurationLoader loader = new MessageConfigurationLoader();
        try {
            MessageConfigurationSnapshot snapshot = loader.load(file(plugin));
            plugin.getLogger().info("Loaded message configuration schema " + snapshot.schemaVersion());
            return new AtomicMessageConfiguration(snapshot);
        } catch (ConfigurationValidationException exception) {
            plugin.getLogger().severe(
                    "EnthusiaStaff message configuration is invalid; startup cannot continue"
            );
            plugin.getLogger().severe("Message configuration error: " + sanitized(exception.getMessage()));
            throw exception;
        }
    }

    private static Path file(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("messages.yml");
    }

    private static String sanitized(String message) {
        return message == null || message.isBlank() ? "messages.yml is invalid" : message;
    }
}

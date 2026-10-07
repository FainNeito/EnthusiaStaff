package net.enthusia.staff.paper.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.enthusia.staff.paper.config.reload.ConfigurationReloadAction;
import net.enthusia.staff.paper.config.reload.MessageConfigurationReloadAction;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageConfigurationRuntime {
    private static AtomicMessageConfiguration active;

    private MessageConfigurationRuntime() {
    }

    public static synchronized ConfigurationReloadAction initialize(
            JavaPlugin plugin,
            ConfigurationReloadAction delegate
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(delegate, "delegate");
        if (active == null) {
            if (Files.notExists(file(plugin))) {
                plugin.saveResource("messages.yml", false);
            }
            MessageConfigurationLoader loader = new MessageConfigurationLoader();
            try {
                active = new AtomicMessageConfiguration(loader.load(file(plugin)));
            } catch (ConfigurationValidationException exception) {
                plugin.getLogger().severe(
                        "EnthusiaStaff message configuration is invalid; startup cannot continue"
                );
                plugin.getLogger().severe("Message configuration error: " + sanitized(exception.getMessage()));
                throw exception;
            }
            plugin.getLogger().info(
                    "Loaded message configuration schema " + snapshot().schemaVersion()
            );
        }
        MessageConfigurationLoader loader = new MessageConfigurationLoader();
        return new MessageConfigurationReloadAction(
                delegate,
                () -> loader.load(file(plugin)),
                active,
                details -> {
                    plugin.getLogger().warning("EnthusiaStaff message configuration reload was rejected");
                    details.forEach(detail -> plugin.getLogger().warning("Reload detail: " + detail));
                }
        );
    }

    public static synchronized MessageConfigurationSnapshot snapshot() {
        if (active == null) {
            throw new IllegalStateException("message configuration runtime has not been initialized");
        }
        return active.snapshot();
    }

    public static MessageCatalog catalog() {
        return snapshot().catalog();
    }

    private static Path file(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("messages.yml");
    }

    private static String sanitized(String message) {
        return message == null || message.isBlank() ? "messages.yml is invalid" : message;
    }
}

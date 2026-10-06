package net.enthusia.discord.platform.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Complete desired state for one platform-owned managed role. */
public record ManagedRoleClaim(
        ManagedRoleKey key,
        String displayName,
        Optional<String> existingDiscordRoleId,
        Set<UUID> desiredMinecraftAccounts) {
    public static final int MAX_DESIRED_ACCOUNTS = 10_000;
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final Pattern DISCORD_ROLE_ID = Pattern.compile("[0-9]{5,30}");

    public ManagedRoleClaim(
            ManagedRoleKey key,
            String displayName,
            Set<UUID> desiredMinecraftAccounts) {
        this(key, displayName, Optional.empty(), desiredMinecraftAccounts);
    }

    public ManagedRoleClaim {
        key = Objects.requireNonNull(key, "key");
        displayName = validateDisplayName(displayName);
        existingDiscordRoleId = validateExistingRoleId(existingDiscordRoleId);
        desiredMinecraftAccounts = validateDesiredAccounts(desiredMinecraftAccounts);
    }

    private static String validateDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || displayName.length() > MAX_DISPLAY_NAME_LENGTH || hasControl(displayName)) {
            throw new IllegalArgumentException("Managed-role display name must be 1-100 printable characters");
        }
        return displayName;
    }

    private static Optional<String> validateExistingRoleId(Optional<String> existingDiscordRoleId) {
        Objects.requireNonNull(existingDiscordRoleId, "existingDiscordRoleId");
        if (existingDiscordRoleId.isPresent()
                && !DISCORD_ROLE_ID.matcher(existingDiscordRoleId.orElseThrow()).matches()) {
            throw new IllegalArgumentException("Existing Discord role ID must be a valid numeric snowflake");
        }
        return existingDiscordRoleId;
    }

    private static Set<UUID> validateDesiredAccounts(Set<UUID> desiredMinecraftAccounts) {
        Objects.requireNonNull(desiredMinecraftAccounts, "desiredMinecraftAccounts");
        if (desiredMinecraftAccounts.size() > MAX_DESIRED_ACCOUNTS) {
            throw new IllegalArgumentException("Managed-role desired account set is too large");
        }
        for (UUID accountId : desiredMinecraftAccounts) {
            if (accountId == null) {
                throw new IllegalArgumentException("Managed-role desired accounts must not contain null");
            }
        }
        return Set.copyOf(desiredMinecraftAccounts);
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}

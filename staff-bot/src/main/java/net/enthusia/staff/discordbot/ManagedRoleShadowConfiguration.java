package net.enthusia.staff.discordbot;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Opt-in, read-only managed-role parity configuration used during DiscordSRV migration. */
record ManagedRoleShadowConfiguration(Duration interval, int maxClaims) {
    static final String ENABLED_ENV = "ENTHUSIA_STAFF_BOT_MANAGED_ROLE_SHADOW_ENABLED";
    static final String INTERVAL_SECONDS_ENV = "ENTHUSIA_STAFF_BOT_MANAGED_ROLE_SHADOW_INTERVAL_SECONDS";
    static final String MAX_CLAIMS_ENV = "ENTHUSIA_STAFF_BOT_MANAGED_ROLE_SHADOW_MAX_CLAIMS";

    private static final int DEFAULT_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_MAX_CLAIMS = 2_000;
    private static final int MIN_INTERVAL_SECONDS = 30;
    private static final int MAX_INTERVAL_SECONDS = 3_600;
    private static final int MAX_CLAIMS_LIMIT = 10_000;

    ManagedRoleShadowConfiguration {
        if (interval == null
                || interval.compareTo(Duration.ofSeconds(MIN_INTERVAL_SECONDS)) < 0
                || interval.compareTo(Duration.ofSeconds(MAX_INTERVAL_SECONDS)) > 0
                || maxClaims < 1
                || maxClaims > MAX_CLAIMS_LIMIT) {
            throw new IllegalArgumentException("managed-role shadow configuration is invalid");
        }
    }

    static Optional<ManagedRoleShadowConfiguration> fromEnvironment(Map<String, String> values) {
        if (values == null) {
            throw new IllegalArgumentException("managed-role shadow configuration values are required");
        }
        boolean any = List.of(ENABLED_ENV, INTERVAL_SECONDS_ENV, MAX_CLAIMS_ENV).stream()
                .anyMatch(name -> present(values.get(name)));
        if (!any) {
            return Optional.empty();
        }

        boolean enabled = booleanValue(values.get(ENABLED_ENV));
        if (!enabled) {
            if (present(values.get(INTERVAL_SECONDS_ENV)) || present(values.get(MAX_CLAIMS_ENV))) {
                throw new IllegalArgumentException(
                        "managed-role shadow tuning requires managed-role-shadow enabled=true");
            }
            return Optional.empty();
        }

        return Optional.of(new ManagedRoleShadowConfiguration(
                Duration.ofSeconds(integer(
                        values.get(INTERVAL_SECONDS_ENV),
                        DEFAULT_INTERVAL_SECONDS,
                        MIN_INTERVAL_SECONDS,
                        MAX_INTERVAL_SECONDS,
                        "managed-role shadow interval")),
                integer(
                        values.get(MAX_CLAIMS_ENV),
                        DEFAULT_MAX_CLAIMS,
                        1,
                        MAX_CLAIMS_LIMIT,
                        "managed-role shadow maximum claims")
        ));
    }

    private static boolean booleanValue(String raw) {
        if (!present(raw)) {
            throw new IllegalArgumentException("managed-role shadow enabled flag is required");
        }
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("managed-role shadow enabled flag must be true or false");
        };
    }

    private static int integer(String raw, int fallback, int minimum, int maximum, String label) {
        if (!present(raw)) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(label + " is outside its safe range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be numeric", exception);
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}

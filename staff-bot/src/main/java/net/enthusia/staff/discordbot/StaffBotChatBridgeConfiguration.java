package net.enthusia.staff.discordbot;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import net.enthusia.staff.common.security.SecretKeyMaterial;

/** Default-off staging configuration for the ephemeral Velocity -> StaffBot chat relay. */
final class StaffBotChatBridgeConfiguration {
    static final String ENABLED_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ENABLED";
    static final String HOST_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_HOST";
    static final String PORT_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_PORT";
    static final String CLIENT_HMAC_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_CLIENT_SECRET";
    static final String PROXY_HMAC_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_PROXY_SECRET";
    static final String TRUST_STORE_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_TRUST_STORE";
    static final String TRUST_STORE_ACCESS_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_TRUST_STORE_PASSWORD";
    static final String ROUTES_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_ROUTES";
    static final String QUEUE_CAPACITY_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_QUEUE_CAPACITY";
    static final String DEDUPE_CAPACITY_ENV = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_DEDUPE_CAPACITY";

    static final String PEER_ID = "STAFFBOT";
    static final String PROXY_ID = "VELOCITY";

    private static final String ENABLED_VALUE = "true";
    private static final String DISABLED_VALUE = "false";
    private static final int DEFAULT_PORT = 28_765;
    private static final int DEFAULT_QUEUE_CAPACITY = 256;
    private static final int DEFAULT_DEDUPE_CAPACITY = 4_096;
    private static final int MAX_QUEUE_CAPACITY = 4_096;
    private static final int MAX_DEDUPE_CAPACITY = 65_536;
    private static final Pattern ROUTE_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final String host;
    private final int port;
    private final SecretKey clientKey;
    private final SecretKey proxyKey;
    private final Path trustStore;
    private final char[] trustStorePassword;
    private final Map<Route, Long> routes;
    private final int queueCapacity;
    private final int dedupeCapacity;

    private StaffBotChatBridgeConfiguration(
            String host,
            int port,
            SecretConfiguration secrets,
            Map<Route, Long> routes,
            QueueBounds bounds
    ) {
        this.host = requireText(host, HOST_ENV);
        this.port = bounded(PORT_ENV, port, 1, 65_535);
        this.clientKey = secrets.clientKey();
        this.proxyKey = secrets.proxyKey();
        this.trustStore = secrets.trustStore().toAbsolutePath().normalize();
        this.trustStorePassword = secrets.trustStorePassword().clone();
        if (this.trustStorePassword.length == 0) {
            throw new IllegalArgumentException(TRUST_STORE_ACCESS_ENV + " is required");
        }
        this.routes = Map.copyOf(routes);
        if (this.routes.isEmpty()) {
            throw new IllegalArgumentException(ROUTES_ENV + " must contain at least one route");
        }
        this.queueCapacity = bounded(
                QUEUE_CAPACITY_ENV, bounds.queueCapacity(), 1, MAX_QUEUE_CAPACITY);
        this.dedupeCapacity = bounded(
                DEDUPE_CAPACITY_ENV, bounds.dedupeCapacity(), 1, MAX_DEDUPE_CAPACITY);
    }

    static Optional<StaffBotChatBridgeConfiguration> fromEnvironment(
            StaffBotEnvironment environment,
            Map<String, String> values
    ) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(values, "values");
        if (!enabled(values.get(ENABLED_ENV))) {
            return Optional.empty();
        }

        long stagingChannelId = stagingChannelId(environment);
        String host = requireText(values.get(HOST_ENV), HOST_ENV);
        int port = integer(values.get(PORT_ENV), DEFAULT_PORT, PORT_ENV, 1, 65_535);
        SecretConfiguration secrets = secretConfiguration(values);
        try {
            return Optional.of(new StaffBotChatBridgeConfiguration(
                    host,
                    port,
                    secrets,
                    routes(requireText(values.get(ROUTES_ENV), ROUTES_ENV), stagingChannelId),
                    queueBounds(values)
            ));
        } finally {
            secrets.clearPassword();
        }
    }

    private static long stagingChannelId(StaffBotEnvironment environment) {
        if (environment != StaffBotEnvironment.STAGING) {
            throw new IllegalArgumentException("Discord chat bridge is staging-only during migration");
        }
        OptionalLong stagingChannel = environment.testChannelId();
        if (stagingChannel.isEmpty()) {
            throw new IllegalArgumentException("staging Discord chat bridge requires a pinned test channel");
        }
        return stagingChannel.getAsLong();
    }

    private static SecretConfiguration secretConfiguration(Map<String, String> values) {
        return new SecretConfiguration(
                SecretKeyMaterial.hmacSha256FromBase64(
                        requireText(values.get(CLIENT_HMAC_ENV), CLIENT_HMAC_ENV)),
                SecretKeyMaterial.hmacSha256FromBase64(
                        requireText(values.get(PROXY_HMAC_ENV), PROXY_HMAC_ENV)),
                Path.of(requireText(values.get(TRUST_STORE_ENV), TRUST_STORE_ENV)),
                requireText(values.get(TRUST_STORE_ACCESS_ENV), TRUST_STORE_ACCESS_ENV).toCharArray()
        );
    }

    private static QueueBounds queueBounds(Map<String, String> values) {
        return new QueueBounds(
                integer(
                        values.get(QUEUE_CAPACITY_ENV),
                        DEFAULT_QUEUE_CAPACITY,
                        QUEUE_CAPACITY_ENV,
                        1,
                        MAX_QUEUE_CAPACITY),
                integer(
                        values.get(DEDUPE_CAPACITY_ENV),
                        DEFAULT_DEDUPE_CAPACITY,
                        DEDUPE_CAPACITY_ENV,
                        1,
                        MAX_DEDUPE_CAPACITY)
        );
    }

    private static boolean enabled(String value) {
        if (value == null || value.isBlank() || DISABLED_VALUE.equalsIgnoreCase(value.trim())) {
            return false;
        }
        if (ENABLED_VALUE.equalsIgnoreCase(value.trim())) {
            return true;
        }
        throw new IllegalArgumentException(ENABLED_ENV + " must be true or false");
    }

    private static Map<Route, Long> routes(String raw, long stagingChannelId) {
        Map<Route, Long> parsed = new LinkedHashMap<>();
        for (String entry : raw.split(";", -1)) {
            int separator = entry.indexOf('=');
            if (separator < 1 || separator != entry.lastIndexOf('=')) {
                throw new IllegalArgumentException(ROUTES_ENV + " must use server/channel=discordChannel entries");
            }
            Route route = route(entry.substring(0, separator).trim());
            long channelId = positiveLong(entry.substring(separator + 1).trim(), ROUTES_ENV);
            if (channelId != stagingChannelId) {
                throw new IllegalArgumentException(
                        "staging Discord chat bridge routes must target the pinned staging channel");
            }
            if (parsed.putIfAbsent(route, channelId) != null) {
                throw new IllegalArgumentException(ROUTES_ENV + " contains a duplicate route");
            }
        }
        return Map.copyOf(parsed);
    }

    private static Route route(String raw) {
        int separator = raw.indexOf('/');
        if (separator < 1 || separator != raw.lastIndexOf('/') || separator == raw.length() - 1) {
            throw new IllegalArgumentException(ROUTES_ENV + " route keys must use server/channel");
        }
        return new Route(
                routeToken(raw.substring(0, separator), "server"),
                routeToken(raw.substring(separator + 1), "channel"));
    }

    private static String routeToken(String value, String label) {
        String normalized = value.trim();
        if (!ROUTE_TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(ROUTES_ENV + " contains an invalid " + label + " token");
        }
        return normalized;
    }

    private static long positiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(label + " channel ids must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " channel ids must be numeric", exception);
        }
    }

    private static int integer(
            String raw,
            int fallback,
            String label,
            int minimum,
            int maximum
    ) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return bounded(label, Integer.parseInt(raw.trim()), minimum, maximum);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer", exception);
        }
    }

    private static int bounded(String label, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    SecretKey clientKey() {
        return clientKey;
    }

    SecretKey proxyKey() {
        return proxyKey;
    }

    Path trustStore() {
        return trustStore;
    }

    char[] trustStorePassword() {
        return trustStorePassword.clone();
    }

    Map<Route, Long> routes() {
        return routes;
    }

    int queueCapacity() {
        return queueCapacity;
    }

    int dedupeCapacity() {
        return dedupeCapacity;
    }

    @Override
    public String toString() {
        return "StaffBotChatBridgeConfiguration[host=" + host
                + ", port=" + port
                + ", routes=" + routes.keySet()
                + ", queueCapacity=" + queueCapacity
                + ", dedupeCapacity=" + dedupeCapacity
                + ", clientKey=<redacted>, proxyKey=<redacted>, trustStorePassword=<redacted>]";
    }

    private record SecretConfiguration(
            SecretKey clientKey,
            SecretKey proxyKey,
            Path trustStore,
            char[] trustStorePassword
    ) {
        private SecretConfiguration {
            Objects.requireNonNull(clientKey, "clientKey");
            Objects.requireNonNull(proxyKey, "proxyKey");
            Objects.requireNonNull(trustStore, "trustStore");
            Objects.requireNonNull(trustStorePassword, "trustStorePassword");
        }

        private void clearPassword() {
            Arrays.fill(trustStorePassword, '\0');
        }
    }

    private record QueueBounds(int queueCapacity, int dedupeCapacity) {
    }

    record Route(String sourceServerId, String logicalChannelId) {
        Route {
            sourceServerId = routeToken(sourceServerId, "server");
            logicalChannelId = routeToken(logicalChannelId, "channel");
        }
    }
}

package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffBotChatBridgeConfigurationTest {
    private static final long STAGING_CHANNEL_ID = 1541286004298752091L;
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void bridgeIsDefaultOff() {
        assertTrue(StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.STAGING, Map.of()).isEmpty());
        assertTrue(StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.STAGING,
                Map.of(StaffBotChatBridgeConfiguration.ENABLED_ENV, "false")).isEmpty());
    }

    @Test
    void parsesExplicitStagingRoutesAndRedactsSecrets() {
        Map<String, String> values = enabledValues();
        StaffBotChatBridgeConfiguration configuration =
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, values).orElseThrow();

        assertEquals("velocity.internal", configuration.host());
        assertEquals(28_765, configuration.port());
        assertEquals(256, configuration.queueCapacity());
        assertEquals(4_096, configuration.dedupeCapacity());
        assertEquals(
                STAGING_CHANNEL_ID,
                configuration.routes().get(new StaffBotChatBridgeConfiguration.Route("SMP", "global")));
        assertEquals(
                STAGING_CHANNEL_ID,
                configuration.routes().get(new StaffBotChatBridgeConfiguration.Route("HUB", "global")));
        assertFalse(configuration.toString().contains(KEY));
        assertFalse(configuration.toString().contains("trust-password"));
        assertTrue(configuration.toString().contains("clientKey=<redacted>"));
    }

    @Test
    void productionCannotEnableMigrationChatBridge() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, enabledValues()));
    }

    @Test
    void routeMustStayOnPinnedStagingChannel() {
        Map<String, String> values = enabledValues();
        values.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=123456789");

        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, values));
    }

    @Test
    void duplicateAndMalformedRoutesAreRejected() {
        Map<String, String> duplicate = enabledValues();
        duplicate.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=" + STAGING_CHANNEL_ID + ";SMP/global=" + STAGING_CHANNEL_ID);
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, duplicate));

        Map<String, String> malformed = enabledValues();
        malformed.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP=" + STAGING_CHANNEL_ID);
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, malformed));
    }

    @Test
    void enabledBridgeRequiresValidSecretsAndBounds() {
        Map<String, String> invalidSecret = enabledValues();
        invalidSecret.put(StaffBotChatBridgeConfiguration.CLIENT_SECRET_ENV, "not-base64");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, invalidSecret));

        Map<String, String> invalidPort = enabledValues();
        invalidPort.put(StaffBotChatBridgeConfiguration.PORT_ENV, "70000");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, invalidPort));

        Map<String, String> invalidQueue = enabledValues();
        invalidQueue.put(StaffBotChatBridgeConfiguration.QUEUE_CAPACITY_ENV, "0");
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.STAGING, invalidQueue));
    }

    private static Map<String, String> enabledValues() {
        Map<String, String> values = new HashMap<>();
        values.put(StaffBotChatBridgeConfiguration.ENABLED_ENV, "true");
        values.put(StaffBotChatBridgeConfiguration.HOST_ENV, "velocity.internal");
        values.put(StaffBotChatBridgeConfiguration.CLIENT_SECRET_ENV, KEY);
        values.put(StaffBotChatBridgeConfiguration.PROXY_SECRET_ENV, KEY);
        values.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ENV, "channel-trust.p12");
        values.put(StaffBotChatBridgeConfiguration.TRUST_STORE_PASSWORD_ENV, "trust-password");
        values.put(
                StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=" + STAGING_CHANNEL_ID + ";HUB/global=" + STAGING_CHANNEL_ID);
        return values;
    }
}

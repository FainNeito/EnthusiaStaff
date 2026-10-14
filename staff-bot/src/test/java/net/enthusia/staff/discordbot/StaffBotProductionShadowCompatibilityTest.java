package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline regression for a second chat-only Discord app while DiscordSRV remains live. */
class StaffBotProductionShadowCompatibilityTest {
    private static final long TEST_CHANNEL = 1541286004298752091L;
    private static final long SEPARATE_CHAT_APP = 1405605074331373718L;
    private static final String MIGRATION_ACK = "I_ACKNOWLEDGE_PRODUCTION_SHADOW_MIGRATION";

    @TempDir Path directory;

    @Test
    void acceptsExistingProductionStartupLineIncludingShortChatFlag() {
        String[] args = {
            "--environment=production", "--token-file=tp", "--moderation-config-file=m",
            "--tunnel-binary-file=cloudflared", "--tunnel-token-file=prod-tunnel",
            "--moderation-web-url=https://staff.enthusia.info", "--chat=c"
        };
        StaffBotCommandLine parsed = StaffBotCommandLine.parse(args);
        assertEquals(StaffBotEnvironment.PRODUCTION, parsed.environment().orElseThrow());
        assertEquals(Path.of("c"), parsed.chatSettingsFile().orElseThrow());
        assertFalse(parsed.toString().contains("--chat=c"));
        assertTrue(parsed.toString().contains("chatSettingsFile=<configured>"));
        assertThrows(IllegalArgumentException.class, () -> StaffBotCommandLine.parse(
                new String[] {"--chat=c", "--chat-bridge-config-file=other"}));
    }

    @Test
    void explicitlyApprovedProductionShadowSendsOnlyToPinnedTestChannel() {
        Map<String, String> values = shadowSettings();
        StaffBotChatBridgeConfiguration bridge = StaffBotChatBridgeConfiguration.fromEnvironment(
                StaffBotEnvironment.PRODUCTION, values).orElseThrow();
        assertEquals(StaffBotChatBridgeConfiguration.Mode.SHADOW, bridge.mode());
        assertEquals(TEST_CHANNEL,
                bridge.routes().get(new StaffBotChatBridgeConfiguration.Route("SMP", "global")));
        assertTrue(bridge.ingressRoutes().isEmpty());
        PublicChatDiscordConfiguration publicChat =
                PublicChatDiscordConfiguration.fromEnvironment(values, true).orElseThrow();
        assertEquals(SEPARATE_CHAT_APP, publicChat.applicationId());

        values.remove(StaffBotChatBridgeConfiguration.MIGRATION_ACK_ENV);
        assertThrows(IllegalArgumentException.class, () ->
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));
    }

    @Test
    void shadowCanNeverRouteIntoLiveProductionChannel() {
        Map<String, String> values = shadowSettings();
        values.put(StaffBotChatBridgeConfiguration.ROUTES_ENV,
                "SMP/global=1541286004298752092");
        assertThrows(IllegalArgumentException.class, () ->
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));
    }

    @Test
    void productionCutoverNeedsSeparateAuthoritativeAcknowledgment() {
        Map<String, String> values = shadowSettings();
        values.put(StaffBotChatBridgeConfiguration.MODE_ENV, "AUTHORITATIVE");
        assertThrows(IllegalArgumentException.class, () ->
                StaffBotChatBridgeConfiguration.fromEnvironment(
                        StaffBotEnvironment.PRODUCTION, values));
    }

    @Test
    void privateChatFileCannotReplaceProductionStaffIdentity() throws Exception {
        Path file = directory.resolve("c");
        Files.writeString(file, "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_MODE=SHADOW\n"
                + "ENTHUSIA_STAFF_BOT_TOKEN=malicious-replacement\n");
        assertThrows(IllegalArgumentException.class, () ->
                StaffBotChatSettingsFile.overlay(file, Map.of(
                        StaffBotConfiguration.ENVIRONMENT_KEY, "production")));
    }

    private static Map<String, String> shadowSettings() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        Map<String, String> settings = new HashMap<>();
        settings.put(StaffBotChatBridgeConfiguration.MODE_ENV, "SHADOW");
        settings.put(StaffBotChatBridgeConfiguration.MIGRATION_ACK_ENV, MIGRATION_ACK);
        settings.put(StaffBotChatBridgeConfiguration.HOST_ENV, "velocity.test.example");
        settings.put(StaffBotChatBridgeConfiguration.PORT_ENV, "28765");
        settings.put(StaffBotChatBridgeConfiguration.CLIENT_HMAC_ENV, key);
        settings.put(StaffBotChatBridgeConfiguration.PROXY_HMAC_ENV, key);
        settings.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ENV, "synthetic-trust.p12");
        settings.put(StaffBotChatBridgeConfiguration.TRUST_STORE_ACCESS_ENV, "synthetic-password");
        settings.put(StaffBotChatBridgeConfiguration.ROUTES_ENV, "SMP/global=" + TEST_CHANNEL);
        settings.put(PublicChatDiscordConfiguration.TOKEN_ENV, "synthetic-secondary-token");
        settings.put(PublicChatDiscordConfiguration.APPLICATION_ID_ENV,
                Long.toString(SEPARATE_CHAT_APP));
        return settings;
    }
}

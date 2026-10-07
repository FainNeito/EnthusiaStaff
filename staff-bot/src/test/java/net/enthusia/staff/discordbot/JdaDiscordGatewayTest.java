package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.enthusia.staff.protocol.ChatBridgeOutboundMessage;
import org.junit.jupiter.api.Test;

class JdaDiscordGatewayTest {
    @Test
    void restrictionRuntimeEnablesOnlyMemberOverrideCache() {
        assertEquals(Set.of(CacheFlag.MEMBER_OVERRIDES), JdaDiscordGateway.requiredCacheFlags());
    }

    @Test
    void managedRoleShadowRequestsOnlyGuildMembersIntent() {
        assertEquals(Set.of(), JdaDiscordGateway.gatewayIntents(false));
        assertEquals(Set.of(GatewayIntent.GUILD_MEMBERS), JdaDiscordGateway.gatewayIntents(true));
    }

    @Test
    void DiscordIngressRequestsMessageIntentsOnlyWhenEnabled() {
        assertEquals(
                Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT),
                JdaDiscordGateway.gatewayIntents(false, true));
        assertEquals(
                Set.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT),
                JdaDiscordGateway.gatewayIntents(true, true));
    }

    @Test
    void productionRoleSyncEnforcementIsRejectedWhileSafeModesRemainAllowed() {
        assertFalse(JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.PRODUCTION, DiscordRoleSyncConfiguration.Mode.ENFORCE));
        assertTrue(JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.PRODUCTION, DiscordRoleSyncConfiguration.Mode.SHADOW));
        assertTrue(JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.STAGING, DiscordRoleSyncConfiguration.Mode.ENFORCE));
        assertThrows(IllegalArgumentException.class, () -> JdaDiscordGateway.roleSyncModeAllowed(
                null, DiscordRoleSyncConfiguration.Mode.SHADOW));
        assertThrows(IllegalArgumentException.class, () -> JdaDiscordGateway.roleSyncModeAllowed(
                StaffBotEnvironment.STAGING, null));
    }

    @Test
    void staleIdentityCallbacksAreRejectedAfterDisconnectOrNewResolution() {
        JdaDiscordGateway.CallbackFence fence = new JdaDiscordGateway.CallbackFence();
        AtomicBoolean invoked = new AtomicBoolean();

        long disconnectedSession = fence.beginResolution();
        fence.invalidate();
        assertFalse(fence.runIfCurrent(disconnectedSession, () -> invoked.set(true)));
        assertFalse(invoked.get());

        long supersededSession = fence.beginResolution();
        long currentSession = fence.beginResolution();
        assertFalse(fence.runIfCurrent(supersededSession, () -> invoked.set(true)));
        assertFalse(invoked.get());
        assertTrue(fence.runIfCurrent(currentSession, () -> invoked.set(true)));
        assertTrue(invoked.get());
    }
    @Test
    void chatContentIncludesSourceAndSenderAndHonorsDiscordLimit() {
        ChatBridgeOutboundMessage shortMessage = chatMessage("hello");
        assertEquals("[SMP] Player: hello", JdaDiscordGateway.chatContent(shortMessage));

        ChatBridgeOutboundMessage longMessage = chatMessage("x".repeat(2_000));
        String rendered = JdaDiscordGateway.chatContent(longMessage);
        assertEquals(2_000, rendered.length());
        assertTrue(rendered.startsWith("[SMP] Player: "));

        ChatBridgeOutboundMessage unicodeBoundary = chatMessage("x".repeat(1_985) + "\uD83D\uDE00");
        String unicodeRendered = JdaDiscordGateway.chatContent(unicodeBoundary);
        assertFalse(Character.isHighSurrogate(unicodeRendered.charAt(unicodeRendered.length() - 1)));
        assertTrue(unicodeRendered.length() <= 2_000);
    }

    private static ChatBridgeOutboundMessage chatMessage(String text) {
        UUID eventId = UUID.randomUUID();
        return new ChatBridgeOutboundMessage(
                eventId,
                "rosechat-mc-" + eventId,
                "rosechat-canonical-" + eventId,
                1_800_000_000_000L,
                1_800_000_030_000L,
                "SMP",
                "global",
                UUID.randomUUID(),
                "Player",
                text
        );
    }

}

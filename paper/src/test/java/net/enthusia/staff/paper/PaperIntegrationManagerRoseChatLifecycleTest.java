package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperIntegrationManagerRoseChatLifecycleTest {

    @Test
    void richRendererLifecycleMatcherAcceptsOnlyExactUpstreamPluginNames() {
        assertTrue(PaperIntegrationManager.isInteractiveChatRendererDependency("InteractiveChat"));
        assertTrue(PaperIntegrationManager.isInteractiveChatRendererDependency(
                "InteractiveChatDiscordSrvAddon"));
        assertFalse(PaperIntegrationManager.isInteractiveChatRendererDependency("interactivechat"));
        assertFalse(PaperIntegrationManager.isInteractiveChatRendererDependency("DiscordSRV"));
        assertFalse(PaperIntegrationManager.isInteractiveChatRendererDependency(null));
    }

    @Test
    void authoritativeCutoverRequiresEveryReplacementSurface() {
        assertTrue(PaperIntegrationManager.authoritativeCutoverReady(
                true, true, true, true, false, false));
        assertTrue(PaperIntegrationManager.authoritativeCutoverReady(
                true, true, true, true, true, true));

        assertFalse(PaperIntegrationManager.authoritativeCutoverReady(
                false, true, true, true, false, false));
        assertFalse(PaperIntegrationManager.authoritativeCutoverReady(
                true, false, true, true, false, false));
        assertFalse(PaperIntegrationManager.authoritativeCutoverReady(
                true, true, false, true, false, false));
        assertFalse(PaperIntegrationManager.authoritativeCutoverReady(
                true, true, true, false, false, false));
        assertFalse(PaperIntegrationManager.authoritativeCutoverReady(
                true, true, true, true, true, false));
    }

    @Test
    void lifecycleMatcherAcceptsOnlyExactRoseChatPluginName() {
        assertTrue(PaperIntegrationManager.isRoseChat("RoseChat"));
        assertFalse(PaperIntegrationManager.isRoseChat("rosechat"));
        assertFalse(PaperIntegrationManager.isRoseChat("EnthusiaStaff"));
        assertFalse(PaperIntegrationManager.isRoseChat(null));
    }
}

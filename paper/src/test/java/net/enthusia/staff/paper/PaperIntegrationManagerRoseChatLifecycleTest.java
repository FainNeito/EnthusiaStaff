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
    void lifecycleMatcherAcceptsOnlyExactRoseChatPluginName() {
        assertTrue(PaperIntegrationManager.isRoseChat("RoseChat"));
        assertFalse(PaperIntegrationManager.isRoseChat("rosechat"));
        assertFalse(PaperIntegrationManager.isRoseChat("EnthusiaStaff"));
        assertFalse(PaperIntegrationManager.isRoseChat(null));
    }
}

package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PolarSpectatorPhaseWiringTest {
    @Test
    void paperPluginPreparesPolarCallbackDuringLoad() throws IOException {
        String plugin = source("src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java");

        assertTrue(plugin.contains("public void onLoad()"));
        assertTrue(plugin.contains("PolarSpectatorPhaseCompatibility.prepareOnLoad(this, featureIssues)"));
        assertTrue(plugin.contains("PolarSpectatorPhaseCompatibility.close(this)"));
    }

    @Test
    void polarHookUsesSupportedLoaderAndEventRepository() throws IOException {
        String hook = source("src/main/java/net/enthusia/staff/paper/integration/PolarSpectatorPhaseHook.java");

        assertTrue(hook.contains("LoaderApi.registerEnableCallback(hook)"));
        assertTrue(hook.contains("events.registerListener(MitigationEvent.class, this::onMitigation)"));
        assertTrue(hook.contains("currentEvents.unregisterListener(currentRegistration)"));
        assertFalse(hook.contains("@EventHandler"));
    }

    @Test
    void runtimeTracksEligibilityWithoutReadingBukkitStateFromPolarCallback() throws IOException {
        String compatibility = source(
                "src/main/java/net/enthusia/staff/paper/integration/PolarSpectatorPhaseCompatibility.java"
        );
        String hook = source("src/main/java/net/enthusia/staff/paper/integration/PolarSpectatorPhaseHook.java");

        assertTrue(compatibility.contains("PlayerGameModeChangeEvent"));
        assertTrue(compatibility.contains("PaperStaffRankResolver.resolve(player::hasPermission)"));
        assertTrue(hook.contains("PolarSpectatorPhaseCompatibility.eligible(event.user().uuid())"));
        assertFalse(hook.contains("bukkitPlayer()"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(relative)).replace("\r\n", "\n");
    }
}

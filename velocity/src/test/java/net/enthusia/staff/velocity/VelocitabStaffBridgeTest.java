package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class VelocitabStaffBridgeTest {
    public interface VanishIntegration {
        boolean canSee(String viewer, String target);
        boolean isVanished(String name);
    }

    public static final class FakeApi {
        private VanishIntegration integration = new VanishIntegration() {
            public boolean canSee(String viewer, String target) { return true; }
            public boolean isVanished(String name) { return false; }
        };
        private String name;
        public VanishIntegration getVanishIntegration() { return integration; }
        public void setVanishIntegration(VanishIntegration value) { integration = value; }
        public Optional<String> getCustomPlayerName(Player player) { return Optional.ofNullable(name); }
        public void setCustomPlayerName(Player player, String value) { name = value; }
    }

    @Test
    void unknownAndStalePresenceFailClosedAndVerifiedMatrixApplies() throws ReflectiveOperationException {
        UUID modId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Player mod = player(modId, "Mod", "mod");
        Player admin = player(adminId, "Admin", "admin");
        FakeApi api = new FakeApi();
        VelocitabStaffBridge bridge = bridge(api, List.of(mod, admin));
        assertTrue(api.integration.isVanished("Admin"));
        assertFalse(api.integration.canSee("Mod", "Admin"));
        var presence = VelocitabStaffBridge.class.getDeclaredField("presence");
        presence.setAccessible(true);
        presence.set(bridge, new StaffTabPresence(Map.of(adminId, StaffRank.ADMIN), Set.of()));
        var verifiedAt = VelocitabStaffBridge.class.getDeclaredField("verifiedAt");
        verifiedAt.setAccessible(true);
        verifiedAt.setLong(bridge, System.nanoTime());
        assertFalse(api.integration.canSee("Mod", "Admin"));
        assertTrue(api.integration.canSee("Admin", "Admin"));
        assertFalse(api.integration.isVanished("Mod"));
        verifiedAt.setLong(bridge, System.nanoTime() - java.time.Duration.ofSeconds(6).toNanos());
        assertTrue(api.integration.isVanished("Mod"));
        assertFalse(api.integration.canSee("Admin", "Mod"));
        bridge.close();
    }

    @Test
    void markersPreserveForeignNamesAndCleanupRestoresOnlyOwnedValues() throws ReflectiveOperationException {
        Player player = player(UUID.randomUUID(), "Staff", "mod");
        FakeApi api = new FakeApi();
        VanishIntegration previous = api.integration;
        VelocitabStaffBridge bridge = bridge(api, List.of(player));
        var update = VelocitabStaffBridge.class.getDeclaredMethod("updateName", Player.class, String.class);
        update.setAccessible(true);
        update.invoke(bridge, player, "<aqua>[V]</aqua> ");
        assertEquals("<aqua>[V]</aqua> Staff", api.name);
        update.invoke(bridge, player, "<aqua>[V]</aqua> ");
        assertEquals("<aqua>[V]</aqua> Staff", api.name);
        update.invoke(bridge, player, "");
        assertEquals(Optional.empty(), api.getCustomPlayerName(player));
        api.name = "Existing nickname";
        update.invoke(bridge, player, "[STAFF] ");
        assertEquals("[STAFF] Existing nickname", api.name);
        bridge.close();
        assertEquals("Existing nickname", api.name);
        assertSame(previous, api.integration);
    }

    @Test
    void cleanupDoesNotOverwriteANewerIntegrationOrName() throws ReflectiveOperationException {
        Player player = player(UUID.randomUUID(), "Staff", "mod");
        FakeApi api = new FakeApi();
        VelocitabStaffBridge bridge = bridge(api, List.of(player));
        var update = VelocitabStaffBridge.class.getDeclaredMethod("updateName", Player.class, String.class);
        update.setAccessible(true);
        update.invoke(bridge, player, "[V] ");
        api.name = "New nickname";
        VanishIntegration newer = new VanishIntegration() {
            public boolean canSee(String viewer, String target) { return false; }
            public boolean isVanished(String name) { return true; }
        };
        api.integration = newer;
        bridge.close();
        assertEquals("New nickname", api.name);
        assertSame(newer, api.integration);
    }

    private static VelocitabStaffBridge bridge(FakeApi api, List<Player> players) throws ReflectiveOperationException {
        ProxyServer proxy = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class}, (ignored, method, args) -> switch (method.getName()) {
                    case "getAllPlayers" -> players;
                    case "getPlayer" -> players.stream().filter(player -> player.getUsername().equals(args[0])).findFirst();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new VelocitabStaffBridge(proxy, LoggerFactory.getLogger(VelocitabStaffBridgeTest.class),
                () -> null, Runnable::run, api, VanishIntegration.class);
    }

    private static Player player(UUID id, String name, String rank) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (ignored, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getUsername" -> name;
                    case "hasPermission" -> args[0].equals("enthusiastaff.rank." + rank);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerNameCompletionTest {
    @Test void allDirectPlayerCommandsHaveRoutesAndDoNotCompleteReasons() {
        for (String name : List.of("punish", "ban", "mute", "warn", "kick", "ipban", "history", "client",
                "inspect", "invsee", "endersee", "freeze", "unfreeze", "report", "removepunishment",
                "unban", "unmute", "unwarn", "removewarning")) {
            assertNotNull(PlayerArgumentRoutes.find(name, new String[]{"P"}), name);
            assertNull(PlayerArgumentRoutes.find(name, new String[]{"Player", "reason"}), name);
        }
    }

    @Test void nestedPlayerPositionsAreRecognizedAndNonPlayerPositionsStayDelegated() {
        for (String[] route : List.of(new String[]{"inspect", "inventory"}, new String[]{"inspect", "ender"},
                new String[]{"inspect", "economy"}, new String[]{"inspect", "items"},
                new String[]{"freeze", "keep"}, new String[]{"freeze", "status"},
                new String[]{"punish", "resume"}, new String[]{"stafftools", "follow"},
                new String[]{"stafftools", "spectate"}, new String[]{"staffflags", "list"},
                new String[]{"staffflags", "add"}, new String[]{"staff", "recover"},
                new String[]{"staffapi", "punish"}, new String[]{"cheattester", "run"},
                new String[]{"cheattester", "cancel"}, new String[]{"fakebase", "create"},
                new String[]{"fakebase", "extend"}, new String[]{"fakebase", "clear"},
                new String[]{"fakebase", "teleport"})) {
            assertNotNull(PlayerArgumentRoutes.find(route[0], new String[]{route[1], ""}));
        }
        assertNotNull(PlayerArgumentRoutes.find("cheattester", new String[]{"base", "create", ""}));
        assertNull(PlayerArgumentRoutes.find("cheattester", new String[]{"base", "status", ""}));
        assertNull(PlayerArgumentRoutes.find("staffflags", new String[]{"resolve", ""}));
        assertNull(PlayerArgumentRoutes.find("punish", new String[]{"approve", ""}));
        assertNull(PlayerArgumentRoutes.find("case", new String[]{""}));
    }

    @Test void permissionRevocationAndHiddenPlayersApplyToEveryCompletion() {
        UUID hidden = UUID.randomUUID();
        Set<String> permissions = new HashSet<>(Set.of("enthusiastaff.history.view"));
        Player viewer = player(UUID.randomUUID(), "Viewer", permissions);
        var completion = new PlayerNameCompletion((v, target) -> !hidden.equals(target), null);
        completion.remember(player(hidden, "Hidden", Set.of()));
        completion.remember(player(UUID.randomUUID(), "Alice", Set.of()));
        assertEquals(List.of("Alice"), completion.complete(viewer, command("history"), "history",
                new String[]{"a"}, null));
        assertEquals(List.of("Alice"), completion.complete(viewer, command("history"), "history",
                new String[]{""}, (s, c, a, args) -> List.of("Hidden")));
        permissions.clear();
        assertEquals(List.of(), completion.complete(viewer, command("history"), "history",
                new String[]{""}, null));
    }

    @Test void publicReportsAndPermissionFilteredInspectorSubcommandsRemainUsable() {
        Player viewer = player(UUID.randomUUID(), "Viewer", Set.of("enthusiastaff.inspect"));
        var completion = new PlayerNameCompletion((v, target) -> true, null);
        completion.remember(player(UUID.randomUUID(), "Alice", Set.of()));
        assertEquals(List.of("Alice"), completion.complete(viewer, command("report"), "report",
                new String[]{"A"}, null));
        assertEquals(List.of("Alice"), completion.complete(viewer, command("inspect"), "inspect",
                new String[]{""}, null));
        assertEquals(List.of("spam"), completion.complete(viewer, command("report"), "report",
                new String[]{"Alice", "s"}, (s, c, a, args) -> List.of("spam")));
    }

    @Test void inventoryOfflineCacheSurvivesButHiddenNamesAreFiltered() {
        UUID hidden = UUID.randomUUID();
        Player viewer = player(UUID.randomUUID(), "Viewer", Set.of("enthusiastaff.inventory.view"));
        var completion = new PlayerNameCompletion((v, target) -> !target.equals(hidden), null);
        completion.remember(player(hidden, "Hidden", Set.of()));
        assertEquals(List.of("Offline"), completion.complete(viewer, command("invsee"), "invsee",
                new String[]{""}, (s, c, a, args) -> List.of("Hidden", "Offline")));
    }

    @Test void consoleOnlyRoutesAndBoundedCaseInsensitiveMatchesAreEnforced() {
        assertFalse(PlayerArgumentRoutes.find("staffapi", new String[]{"punish", ""})
                .allowed(ignored -> true, false));
        assertTrue(PlayerArgumentRoutes.find("staff", new String[]{"recover", ""})
                .allowed(ignored -> false, true));
        var names = IntStream.range(0, 100).mapToObj(i -> "Player%03d".formatted(i)).toList();
        assertEquals(50, PlayerNameCompletion.matches(names, "pL").size());
        assertEquals(List.of("Alice"), PlayerNameCompletion.matches(List.of("Alice", "Alice", "Bob"), "a"));
    }

    private static Command command(String name) {
        return new Command(name) {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
        };
    }

    private static Player player(UUID id, String name, Set<String> permissions) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "hasPermission" -> permissions.contains(args[0]);
                    case "isOnline" -> true;
                    default -> null;
                });
    }
}

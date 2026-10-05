package net.enthusia.staff.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Player argument positions only: reason, case, draft, and confirmation inputs stay delegated. */
public final class PlayerArgumentRoutes {
    private static final Map<String, String> DIRECT = Map.ofEntries(
            Map.entry("punish", "enthusiastaff.punish"), Map.entry("ban", "enthusiastaff.punish"),
            Map.entry("mute", "enthusiastaff.punish"), Map.entry("warn", "enthusiastaff.punish"),
            Map.entry("kick", "enthusiastaff.punish"), Map.entry("ipban", "enthusiastaff.punish.ip"),
            Map.entry("history", "enthusiastaff.history.view"), Map.entry("client", "enthusiastaff.client"),
            Map.entry("inspect", "enthusiastaff.inspect"), Map.entry("invsee", "enthusiastaff.inventory.view"),
            Map.entry("endersee", "enthusiastaff.inventory.view"), Map.entry("freeze", "enthusiastaff.freeze"),
            Map.entry("unfreeze", "enthusiastaff.freeze"), Map.entry("report", ""),
            Map.entry("removepunishment", "enthusiastaff.remove"), Map.entry("unban", "enthusiastaff.remove"),
            Map.entry("unmute", "enthusiastaff.remove"), Map.entry("unwarn", "enthusiastaff.remove"),
            Map.entry("removewarning", "enthusiastaff.remove"));
    private static final Set<String> FAKE_BASE_TARGETS = Set.of("create", "extend", "clear", "teleport");
    private static final Set<String> INSPECT_TARGETS = Set.of("inventory", "ender", "economy", "items");

    private PlayerArgumentRoutes() { }

    public static boolean supports(String command) {
        return DIRECT.containsKey(command) || Set.of("stafftools", "staffflags", "staff", "staffapi",
                "cheattester", "fakebase").contains(command);
    }

    public static Route find(String command, String[] args) {
        String name = command.toLowerCase(Locale.ROOT);
        if (args.length == 0) { return null; }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && DIRECT.containsKey(name)) {
            return new Route(DIRECT.get(name), false, rootKeywords(name));
        }
        return nested(name, first, args);
    }

    private static Route nested(String name, String first, String[] args) {
        if (args.length == 2) {
            return secondArgument(name, first);
        }
        if (args.length == 3 && name.equals("cheattester") && first.equals("base")
                && FAKE_BASE_TARGETS.contains(args[1].toLowerCase(Locale.ROOT))) {
            return new Route("enthusiastaff.cheattester.fake-base", false, List.of());
        }
        return null;
    }

    private static Route secondArgument(String name, String first) {
        return switch (name) {
            case "inspect" -> INSPECT_TARGETS.contains(first)
                    ? new Route(inspectPermission(first), false, List.of()) : null;
            case "freeze" -> Set.of("keep", "status").contains(first)
                    ? new Route("enthusiastaff.freeze", false, List.of()) : null;
            case "punish" -> first.equals("resume")
                    ? new Route("enthusiastaff.punish", false, List.of()) : null;
            case "stafftools" -> Set.of("follow", "spectate").contains(first)
                    ? new Route("enthusiastaff.stafftools.spectate", false, List.of()) : null;
            case "staffflags" -> flagRoute(first);
            case "staff" -> first.equals("recover")
                    ? new Route("enthusiastaff.staffmode", true, List.of()) : null;
            case "staffapi" -> first.equals("punish") ? new Route("", true, List.of()) : null;
            case "cheattester" -> Set.of("run", "cancel").contains(first)
                    ? new Route("enthusiastaff.cheattester", false, List.of()) : null;
            case "fakebase" -> FAKE_BASE_TARGETS.contains(first)
                    ? new Route("enthusiastaff.cheattester.fake-base", false, List.of()) : null;
            default -> null;
        };
    }

    private static Route flagRoute(String first) {
        return switch (first) {
            case "list" -> new Route("enthusiastaff.investigation.view", false, List.of());
            case "add" -> new Route("enthusiastaff.investigation.edit", false, List.of());
            default -> null;
        };
    }

    private static String inspectPermission(String action) {
        return switch (action) {
            case "economy" -> "enthusiastaff.confiscate.economy";
            case "items" -> "enthusiastaff.confiscate.items";
            default -> "enthusiastaff.inventory.view";
        };
    }

    private static List<String> rootKeywords(String name) {
        return switch (name) {
            case "inspect" -> List.of("inventory", "ender", "economy", "items");
            case "freeze" -> List.of("keep", "list", "status");
            case "punish" -> List.of("resume", "requests", "review", "approve", "deny");
            default -> List.of();
        };
    }

    public record Route(String permission, boolean consoleOnly, List<String> keywords) {
        public boolean allowed(Predicate<String> permissions, boolean console) {
            return (!consoleOnly || console) && (permission.isEmpty() || console || permissions.test(permission));
        }
    }
}

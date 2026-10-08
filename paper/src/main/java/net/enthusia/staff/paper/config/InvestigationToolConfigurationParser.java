package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Validate new operator settings before startup/reload and capture immutable restart-only inputs. */
final class InvestigationToolConfigurationParser {
    private static final List<String> TOOLS = List.of("random-teleport", "player-inspector", "freeze", "reports",
            "cheat-tester", "spectate", "vanish", "staff-chat", "staff-tools");

    Map<String, String> parse(JsonNode root, List<String> errors) {
        JsonNode tools = ConfigurationNodes.optionalMapping(root, "staff-tools", "staff-tools", errors);
        JsonNode patrol = ConfigurationNodes.optionalMapping(tools, "random-teleport", "staff-tools.random-teleport", errors);
        ConfigurationNodes.boundedInteger(patrol, "recent-targets", "staff-tools.random-teleport.recent-targets", 20, 0, 1000, errors);
        ConfigurationNodes.boundedLong(patrol, "idle-seconds", "staff-tools.random-teleport.idle-seconds", 300, 30, 3600, errors);
        ConfigurationNodes.bool(patrol, "skip-idle", "staff-tools.random-teleport.skip-idle", false, errors);
        JsonNode slots = ConfigurationNodes.optionalMapping(tools, "slots", "staff-tools.slots", errors);
        validateSlots(slots, errors);
        JsonNode investigation = ConfigurationNodes.optionalMapping(root, "investigation", "investigation", errors);
        JsonNode alerts = ConfigurationNodes.optionalMapping(investigation, "join-alerts", "investigation.join-alerts", errors);
        ConfigurationNodes.bool(alerts, "enabled", "investigation.join-alerts.enabled", false, errors);
        JsonNode flags = ConfigurationNodes.optionalMapping(investigation, "flags", "investigation.flags", errors);
        JsonNode categories = flags == null ? null : flags.get("categories");
        validateCategories(categories, errors);
        Map<String, String> result = new TreeMap<>();
        if (tools != null) {
            flatten("staff-tools.slots", tools.get("slots"), result);
            flatten("staff-tools.random-teleport", tools.get("random-teleport"), result);
            flatten("staff-tools.cooldowns", tools.get("cooldowns"), result);
        }
        flatten("investigation", investigation, result);
        return Map.copyOf(result);
    }

    private static void validateSlots(JsonNode slots, List<String> errors) {
        HashSet<Integer> occupied = new HashSet<>();
        for (int index = 0; index < TOOLS.size(); index++) {
            int slot = ConfigurationNodes.boundedInteger(slots, TOOLS.get(index), "staff-tools.slots." + TOOLS.get(index),
                    index, 0, 8, errors);
            if (!occupied.add(slot)) { errors.add("staff-tools.slots must contain unique values"); }
        }
        if (slots != null) { slots.fieldNames().forEachRemaining(key -> { if (!TOOLS.contains(key)) { errors.add("Unknown staff-tools.slots ID: " + key); } }); }
    }

    private static void validateCategories(JsonNode categories, List<String> errors) {
        if (categories != null) {
            HashSet<String> seen = new HashSet<>();
            if (!categories.isArray() || categories.isEmpty() || categories.size() > 32) {
                errors.add("investigation.flags.categories must contain 1..32 IDs");
            } else {
                for (JsonNode category : categories) {
                    if (!validCategory(category, seen)) {
                        errors.add("investigation.flags.categories must contain unique lowercase IDs");
                    }
                }
            }
        }
    }

    private static boolean validCategory(JsonNode category, java.util.Set<String> seen) {
        return category.isTextual() && category.asText().matches("[a-z][a-z0-9-]{0,31}") && seen.add(category.asText());
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> result) {
        if (node == null) { return; }
        if (node.isObject()) { node.properties().forEach(entry -> flatten(prefix + '.' + entry.getKey(), entry.getValue(), result)); }
        else { result.put(prefix, node.toString()); }
    }
}

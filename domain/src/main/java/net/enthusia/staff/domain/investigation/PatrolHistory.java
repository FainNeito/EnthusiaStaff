package net.enthusia.staff.domain.investigation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Prefer fresh targets while retaining a fallback when all eligible players were visited. */
public final class PatrolHistory {
    private final Object lock = new Object();
    private final int actorCapacity;
    private final int visits;
    private final Map<UUID, ArrayDeque<UUID>> history = new LinkedHashMap<>();

    public PatrolHistory(int actorCapacity, int visits) {
        if (actorCapacity < 1 || actorCapacity > 10_000 || visits < 0 || visits > 1000) {
            throw new IllegalArgumentException("patrol bounds exceeded");
        }
        this.actorCapacity = actorCapacity;
        this.visits = visits;
    }

    public List<UUID> preferFresh(UUID actor, List<UUID> candidates) {
        synchronized (lock) {
            var seen = new HashSet<>(history.getOrDefault(actor, new ArrayDeque<>()));
            var fresh = candidates.stream().filter(id -> !seen.contains(id)).toList();
            // Keep stale candidates for execution-time fallback, after every fresh candidate.
            return java.util.stream.Stream.concat(fresh.stream(), candidates.stream().filter(seen::contains)).toList();
        }
    }

    public void visited(UUID actor, UUID target) {
        synchronized (lock) {
            if (visits == 0) { return; }
            if (!history.containsKey(actor) && history.size() >= actorCapacity) {
                history.remove(history.keySet().iterator().next());
            }
            var queue = history.computeIfAbsent(actor, ignored -> new ArrayDeque<>());
            queue.remove(target);
            queue.addLast(target);
            while (queue.size() > visits) { queue.removeFirst(); }
        }
    }

    public void forget(UUID player) {
        synchronized (lock) {
            history.remove(player);
            history.values().forEach(queue -> queue.remove(player));
        }
    }
}

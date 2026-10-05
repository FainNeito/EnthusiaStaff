package net.enthusia.staff.domain.investigation;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded per-viewer/target and global-per-viewer reconnect notification budgets. */
public final class JoinAlertLimiter {
    private record Pair(UUID viewer, UUID target) { }
    private final Map<Pair, Instant> pairs = new LinkedHashMap<>();
    private final Map<UUID, Instant> viewers = new LinkedHashMap<>();
    private final int capacity;
    public JoinAlertLimiter(int capacity) {
        if (capacity < 1 || capacity > 10_000) { throw new IllegalArgumentException("capacity must be 1..10000"); }
        this.capacity = capacity;
    }
    public synchronized boolean acquire(UUID viewer, UUID target, Instant now) {
        Pair pair = new Pair(viewer, target);
        if (pairs.getOrDefault(pair, Instant.MIN).isAfter(now.minus(Duration.ofMinutes(5)))
                || viewers.getOrDefault(viewer, Instant.MIN).isAfter(now.minusSeconds(2))) { return false; }
        if (!pairs.containsKey(pair) && pairs.size() >= capacity) { pairs.remove(pairs.keySet().iterator().next()); }
        if (!viewers.containsKey(viewer) && viewers.size() >= capacity) { viewers.remove(viewers.keySet().iterator().next()); }
        pairs.put(pair, now);
        viewers.put(viewer, now);
        return true;
    }
    public synchronized void clear() { pairs.clear(); viewers.clear(); }
}

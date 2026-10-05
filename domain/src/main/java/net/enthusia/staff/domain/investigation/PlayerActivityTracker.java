package net.enthusia.staff.domain.investigation;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded local-session observations, never an automated cheating verdict. */
public final class PlayerActivityTracker {
    public enum Activity { LEFT_CLICK, RIGHT_CLICK, CROUCH, BLOCK_PLACE, BLOCK_BREAK, MOVE }
    private final Clock clock;
    private final int capacity;
    private final Map<UUID, EnumMap<Activity, Instant>> sessions = new LinkedHashMap<>();

    public PlayerActivityTracker(Clock clock, int capacity) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (capacity < 1 || capacity > 10_000) {
            throw new IllegalArgumentException("activity capacity must be 1..10000");
        }
        this.capacity = capacity;
    }

    public synchronized void record(UUID playerId, Activity activity) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(activity, "activity");
        if (!sessions.containsKey(playerId) && sessions.size() >= capacity) {
            sessions.remove(sessions.keySet().iterator().next());
        }
        sessions.computeIfAbsent(playerId, ignored -> new EnumMap<>(Activity.class)).put(activity, clock.instant());
    }

    public synchronized Map<Activity, Instant> snapshot(UUID playerId) {
        Map<Activity, Instant> values = sessions.get(playerId);
        return values == null ? Map.of() : Map.copyOf(values);
    }

    public synchronized void forget(UUID playerId) { sessions.remove(playerId); }
    public synchronized void clear() { sessions.clear(); }
}

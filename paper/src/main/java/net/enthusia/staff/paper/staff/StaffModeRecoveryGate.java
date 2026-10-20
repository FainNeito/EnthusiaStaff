package net.enthusia.staff.paper.staff;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

final class StaffModeRecoveryGate {
    private final Set<UUID> transitions;
    private final Set<UUID> retryable = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Object> attempts = new ConcurrentHashMap<>();

    StaffModeRecoveryGate(Set<UUID> transitions) {
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    boolean begin(UUID playerId) {
        return beginAttempt(playerId) != null;
    }

    synchronized Object beginAttempt(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (transitions.add(playerId) || retryable.remove(playerId)) {
            Object attempt = new Object();
            attempts.put(playerId, attempt);
            return attempt;
        }
        return null;
    }

    synchronized Object attempt(UUID playerId) {
        return attempts.get(playerId);
    }

    synchronized Object ensureAttempt(UUID playerId) {
        return transitions.contains(playerId) ? attempts.computeIfAbsent(playerId, ignored -> new Object()) : null;
    }

    synchronized boolean owns(UUID playerId, Object attempt) {
        return attempt != null && attempts.get(playerId) == attempt && transitions.contains(playerId);
    }

    synchronized boolean withAttempt(UUID playerId, Object attempt, Runnable operation) {
        if (!owns(playerId, attempt)) {
            return false;
        }
        operation.run();
        return true;
    }

    synchronized boolean complete(UUID playerId, Object attempt, BooleanSupplier cleanup) {
        if (!owns(playerId, attempt) || !cleanup.getAsBoolean()) {
            return false;
        }
        clear(playerId);
        return true;
    }

    synchronized boolean retry(UUID playerId, Object attempt) {
        if (!owns(playerId, attempt)) {
            return false;
        }
        retry(playerId);
        return true;
    }

    synchronized void retry(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        attempts.remove(playerId);
        transitions.add(playerId);
        retryable.add(playerId);
    }

    synchronized void clear(UUID playerId) {
        attempts.remove(playerId);
        retryable.remove(playerId);
        transitions.remove(playerId);
    }
}

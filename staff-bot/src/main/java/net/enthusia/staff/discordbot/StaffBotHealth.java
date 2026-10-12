package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Privacy-safe process health state; no user, message, punishment, or credential data is stored here. */
public final class StaffBotHealth {
    public enum Phase {
        STARTING,
        CONNECTING,
        READY,
        DISCONNECTED,
        FAILED,
        STOPPING,
        STOPPED
    }

    private final StaffBotEnvironment environment;
    private final AtomicReference<Snapshot> snapshot;
    private final AtomicBoolean failedEver = new AtomicBoolean();
    private volatile BooleanSupplier criticalReadiness = () -> true;
    private final AtomicBoolean criticalUnavailable = new AtomicBoolean();
    private volatile Supplier<AiMirrorRecoveryMetrics> stagingMirrorMetrics;

    public StaffBotHealth(StaffBotEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.snapshot = new AtomicReference<>(new Snapshot(Phase.STARTING, "process_starting", Instant.now(), 0));
    }

    public void transition(Phase phase, String reason) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(reason, "reason");
        if (phase == Phase.FAILED) {
            failedEver.set(true);
        }
        Instant changedAt = Instant.now();
        snapshot.updateAndGet(current -> new Snapshot(phase, reason, changedAt, current.rejectedWork()));
    }

    public void recordRejectedWork() {
        snapshot.updateAndGet(current -> new Snapshot(
                current.phase(),
                current.reason(),
                current.changedAt(),
                Math.incrementExact(current.rejectedWork())));
    }

    /**
     * Attach a staging-only critical subsystem readiness check. Its result
     * contains no message identifiers, tokens, source text or file paths.
     * The existing READY phase remains unaffected when no check is installed.
     */
    void requireCriticalReadiness(BooleanSupplier check) {
        criticalReadiness = Objects.requireNonNull(check, "check");
    }

    /**
     * A strictly opt-in, loopback-only aggregate telemetry supplier for
     * durable mirror STAGING. The absence of the supplier preserves the
     * existing health response shape, including on PRODUCTION.
     */
    void requireStagingMirrorMetrics(Supplier<AiMirrorRecoveryMetrics> supplier) {
        if (environment != StaffBotEnvironment.STAGING) {
            throw new IllegalStateException("mirror metrics require staging");
        }
        stagingMirrorMetrics = Objects.requireNonNull(supplier, "mirror metrics");
    }

    Optional<AiMirrorRecoveryMetrics> stagingMirrorMetrics() {
        Supplier<AiMirrorRecoveryMetrics> current = stagingMirrorMetrics;
        if (environment != StaffBotEnvironment.STAGING || current == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(current.get());
        } catch (RuntimeException unavailable) {
            // This path must not expose receipt data or diagnostics.
            return Optional.empty();
        }
    }

    public Snapshot snapshot() {
        Snapshot current = snapshot.get();
        if (current.phase() != Phase.READY) {
            return current;
        }
        try {
            if (!criticalReadiness.getAsBoolean()) {
                criticalUnavailable.set(true);
            }
        } catch (RuntimeException unavailable) {
            // This readiness callback must never expose sensitive error detail.
            criticalUnavailable.set(true);
        }
        if (!criticalUnavailable.get()) {
            return current;
        }
        failedEver.set(true);
        return new Snapshot(Phase.DISCONNECTED, "mirror_recovery_unavailable",
                current.changedAt(), current.rejectedWork());
    }

    public StaffBotEnvironment environment() {
        return environment;
    }

    public boolean isReady() {
        return snapshot().phase() == Phase.READY;
    }

    public boolean isLive() {
        Phase phase = snapshot.get().phase();
        return phase != Phase.FAILED && phase != Phase.STOPPED;
    }

    public boolean failedEver() {
        return failedEver.get();
    }

    public record Snapshot(Phase phase, String reason, Instant changedAt, long rejectedWork) {
        public Snapshot {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(changedAt, "changedAt");
            if (rejectedWork < 0) {
                throw new IllegalArgumentException("rejected work count cannot be negative");
            }
        }

        public boolean ready() {
            return phase == Phase.READY;
        }

        public boolean live() {
            return phase != Phase.FAILED && phase != Phase.STOPPED;
        }
    }
}

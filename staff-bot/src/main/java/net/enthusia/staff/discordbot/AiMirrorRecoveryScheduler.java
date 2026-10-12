package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Inert, slow recovery scheduler. NOT created by StaffBotRuntime and not
 * attached to JDA or any environment setting. It may only retry pre-existing,
 * authenticated journal receipts. One daemon, one attempt per minute at most.
 *
 * New Discord receipts must eventually be persisted through a separate,
 * bounded, nonblocking off-JDA handoff before this is safe to activate.
 */
final class AiMirrorRecoveryScheduler implements AutoCloseable {
    private static final long SLOW_INTERVAL_MINUTES = 1L;
    private final AiMirrorEncryptedOutbox store;
    private final AiMirrorReceiptReplay replay;
    private final ScheduledThreadPoolExecutor worker;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicLong confirmed = new AtomicLong();
    private final AtomicLong transientFailures = new AtomicLong();
    private final AtomicLong terminal = new AtomicLong();
    private final AtomicLong exhausted = new AtomicLong();
    private final AtomicLong storageFailures = new AtomicLong();

    record Stats(long confirmed, long transientFailures, long terminal,
                 long exhausted, long storageFailures) {}

    AiMirrorRecoveryScheduler(AiMirrorEncryptedOutbox store, AiMirrorReceiptReplay replay) {
        this.store = Objects.requireNonNull(store, "store");
        this.replay = Objects.requireNonNull(replay, "replay");
        this.worker = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "StaffBot-AI-Mirror-Recovery-Prototype");
            thread.setDaemon(true);
            return thread;
        });
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    boolean start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return false;
        }
        try {
            worker.execute(this::recoverOne);
            return true;
        } catch (RejectedExecutionException closedWorker) {
            failed.set(true);
            return false;
        }
    }

    private void recoverOne() {
        if (closed.get() || failed.get()) {
            return;
        }
        try {
            List<AiMirrorEncryptedOutbox.Saved> available = store.recover();
            if (!available.isEmpty() && !closed.get()) {
                recordOutcome(replay.attemptOne(available.getFirst()));
            }
        } catch (IOException | InterruptedException | RuntimeException error) {
            // Storage/corruption/reconfiguration failure: fail closed. Never
            // log the exception; it may contain receipt metadata or chat text.
            storageFailures.incrementAndGet();
            failed.set(true);
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (!closed.get() && !failed.get()) {
                scheduleNext();
            }
        }
    }

    private void recordOutcome(AiMirrorReceiptReplay.Outcome outcome) {
        switch (outcome) {
            case CONFIRMED -> confirmed.incrementAndGet();
            case TRANSIENT -> transientFailures.incrementAndGet();
            case TERMINAL -> terminal.incrementAndGet();
            case EXHAUSTED -> exhausted.incrementAndGet();
        }
    }

    private void scheduleNext() {
        try {
            worker.schedule(this::recoverOne, SLOW_INTERVAL_MINUTES, TimeUnit.MINUTES);
        } catch (RejectedExecutionException closedWorker) {
            if (!closed.get()) {
                failed.set(true);
            }
        }
    }

    Stats stats() {
        return new Stats(confirmed.get(), transientFailures.get(),
                terminal.get(), exhausted.get(), storageFailures.get());
    }

    boolean failed() {
        return failed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            worker.shutdownNow();
        }
    }
}

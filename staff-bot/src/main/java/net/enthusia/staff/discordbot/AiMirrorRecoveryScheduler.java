package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Inert, slow recovery scheduler. NOT created by StaffBotRuntime and not
 * attached to JDA or any environment setting. It may only retry pre-existing,
 * authenticated journal receipts. One daemon, with a durable 60-second
 * conservative interval or 5-second interval following a confirmed HTTP 200
 * and exact ACK. Any failed attempt reverts to conservative pacing.
 *
 * New Discord receipts must eventually be persisted through a separate,
 * bounded, nonblocking off-JDA handoff before this is safe to activate.
 */
final class AiMirrorRecoveryScheduler implements AutoCloseable {
    private static final long IDLE_POLL_MILLIS = 60_000L;
    private final AiMirrorEncryptedOutbox store;
    private final AiMirrorReceiptReplay replay;
    private final ScheduledThreadPoolExecutor worker;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private volatile ScheduledFuture<?> next;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicLong confirmed = new AtomicLong();
    private final AtomicLong transientFailures = new AtomicLong();
    private final AtomicLong terminal = new AtomicLong();
    private final AtomicLong exhausted = new AtomicLong();
    private final AtomicLong deferred = new AtomicLong();
    private final AtomicLong storageFailures = new AtomicLong();

    record Stats(long confirmed, long transientFailures, long terminal,
                 long exhausted, long deferred, long storageFailures,
                 long expiredReceipts) {}

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
        return resume();
    }

    boolean resume() {
        if (!started.get() || closed.get() || failed.get()
                || !enabled.compareAndSet(false, true)) {
            return false;
        }
        long current = generation.get();
        try {
            worker.execute(() -> recoverOne(current));
            return true;
        } catch (RejectedExecutionException closedWorker) {
            stopOnFailure();
            return false;
        }
    }

    void pause() {
        enabled.set(false);
        generation.incrementAndGet();
        ScheduledFuture<?> pending = next;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private boolean running(long expectedGeneration) {
        return !closed.get() && !failed.get() && enabled.get()
                && generation.get() == expectedGeneration;
    }

    private void recoverOne(long expectedGeneration) {
        if (!running(expectedGeneration)) {
            return;
        }
        try {
            processOldestReceipt();
        } catch (InterruptedException interrupted) {
            stopOnFailure();
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failedReceipt) {
            stopOnFailure();
        } finally {
            if (running(expectedGeneration)) {
                scheduleNext(expectedGeneration);
            }
        }
    }

    private void processOldestReceipt() throws IOException, InterruptedException {
        List<AiMirrorEncryptedOutbox.Saved> available = store.recover();
        if (!available.isEmpty() && !closed.get()) {
            recordOutcome(replay.attemptOne(available.getFirst()));
        }
    }

    private void stopOnFailure() {
        // Never log the exception: it could expose private receipt metadata.
        storageFailures.incrementAndGet();
        failed.set(true);
    }

    private void recordOutcome(AiMirrorReceiptReplay.Outcome outcome) {
        switch (outcome) {
            case CONFIRMED -> confirmed.incrementAndGet();
            case TRANSIENT -> transientFailures.incrementAndGet();
            case TERMINAL -> terminal.incrementAndGet();
            case EXHAUSTED -> exhausted.incrementAndGet();
            case DEFERRED -> deferred.incrementAndGet();
            default -> throw new IllegalStateException("unknown recovery outcome");
        }
    }

    private void scheduleNext(long expectedGeneration) {
        try {
            // Authenticate the persisted rate state before deciding when to
            // poll. Do not spin at 5 seconds with no journal backlog.
            long delay = store.recoveryPollMillis();
            if (store.recover().isEmpty()) {
                delay = IDLE_POLL_MILLIS;
            }
            ScheduledFuture<?> scheduled = worker.schedule(
                    () -> recoverOne(expectedGeneration), delay, TimeUnit.MILLISECONDS);
            next = scheduled;
            if (!running(expectedGeneration)) {
                scheduled.cancel(false);
            }
        } catch (IOException | RuntimeException unavailable) {
            stopOnFailure();
        }
    }

    Stats stats() {
        return new Stats(confirmed.get(), transientFailures.get(),
                terminal.get(), exhausted.get(), deferred.get(),
                storageFailures.get(), store.expiredRecordCount());
    }

    boolean failed() {
        return failed.get();
    }

    boolean awaitStopped(java.time.Duration deadline) throws InterruptedException {
        return worker.awaitTermination(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pause();
            worker.shutdownNow();
        }
    }
}

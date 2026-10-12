package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Inert immediate post-persistence registration worker, not installed by
 * StaffBotRuntime. A future reviewed ingress integration may offer receipts
 * only after encrypted fsync. Never invoke from a JDA callback.
 *
 * <p>A full/paused queue keeps the committed record for slow recovery.
 * One HTTP attempt is made per offered record and does not consume the
 * separate persistent slow-recovery rate/attempt budget.</p>
 */
final class AiMirrorImmediateRegistration implements AutoCloseable {
    private static final int PENDING_CAPACITY = 64;
    private static final int HTTP_OK = 200;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_UNAVAILABLE = 503;

    record Stats(long confirmed, long transientRetained, long terminalDiscarded,
                 long deferredToRecovery, long storageFailures) {}

    private final AiMirrorEncryptedOutbox store;
    private final AiMirrorPublicationConfig config;
    private final AiMirrorRegistrationPublisher.DetailedPoster poster;
    private final ThreadPoolExecutor worker;
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicLong confirmed = new AtomicLong();
    private final AtomicLong transientRetained = new AtomicLong();
    private final AtomicLong terminalDiscarded = new AtomicLong();
    private final AtomicLong deferredToRecovery = new AtomicLong();
    private final AtomicLong storageFailures = new AtomicLong();
    private final Object admissionLock = new Object();

    AiMirrorImmediateRegistration(AiMirrorEncryptedOutbox store,
            AiMirrorPublicationConfig config,
            AiMirrorRegistrationPublisher.DetailedPoster poster) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.poster = Objects.requireNonNull(poster, "poster");
        if (!store.accepts(config)) {
            throw new IllegalArgumentException("immediate registration public scope mismatch");
        }
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(PENDING_CAPACITY), task -> {
                    Thread thread = new Thread(task, "StaffBot-AI-Mirror-Immediate-Prototype");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    void resume() {
        synchronized (admissionLock) {
            if (!closed.get() && !failed.get()) {
                accepting.set(true);
            }
        }
    }

    boolean offer(AiMirrorEncryptedOutbox.Saved receipt) {
        Objects.requireNonNull(receipt, "receipt");
        synchronized (admissionLock) {
            if (!accepting.get() || closed.get() || failed.get()) {
                deferredToRecovery.incrementAndGet();
                return false;
            }
            try {
                worker.execute(() -> sendCommitted(receipt));
                return true;
            } catch (RejectedExecutionException saturated) {
                deferredToRecovery.incrementAndGet();
                return false;
            }
        }
    }

    private void sendCommitted(AiMirrorEncryptedOutbox.Saved receipt) {
        if (!accepting.get() || closed.get() || failed.get()) {
            deferredToRecovery.incrementAndGet();
            return;
        }
        final AiMirrorRegistrationPublisher.PostResult response;
        try {
            HttpRequest request = AiMirrorRegistrationPublisher.request(
                    config, receipt.publication());
            response = Objects.requireNonNull(poster.post(request), "poster result");
        } catch (IOException networkFailure) {
            transientRetained.incrementAndGet();
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            transientRetained.incrementAndGet();
            return;
        } catch (RuntimeException invalidRequest) {
            failClosed();
            return;
        }
        if (transientStatus(response)) {
            transientRetained.incrementAndGet();
            return;
        }
        acknowledgeResponse(receipt, response);
    }

    private void acknowledgeResponse(AiMirrorEncryptedOutbox.Saved receipt,
            AiMirrorRegistrationPublisher.PostResult response) {
        try {
            store.acknowledge(receipt);
            if (response.status() == HTTP_OK) {
                confirmed.incrementAndGet();
            } else {
                terminalDiscarded.incrementAndGet();
            }
        } catch (IOException | RuntimeException storageFault) {
            failClosed();
        }
    }

    private static boolean transientStatus(AiMirrorRegistrationPublisher.PostResult result) {
        if (result.status() == HTTP_CONFLICT) {
            return "canonical_pending".equals(result.errorCode());
        }
        return result.status() == HTTP_UNAVAILABLE
                && ("timeout".equals(result.errorCode())
                || "storage_unavailable".equals(result.errorCode()));
    }

    private void failClosed() {
        storageFailures.incrementAndGet();
        failed.set(true);
        pause();
    }

    void pause() {
        synchronized (admissionLock) {
            accepting.set(false);
            List<Runnable> pending = new ArrayList<>();
            worker.getQueue().drainTo(pending);
            deferredToRecovery.addAndGet(pending.size());
        }
    }

    Stats stats() {
        return new Stats(confirmed.get(), transientRetained.get(),
                terminalDiscarded.get(), deferredToRecovery.get(), storageFailures.get());
    }

    boolean failed() {
        return failed.get();
    }

    boolean awaitStopped(java.time.Duration limit) throws InterruptedException {
        return worker.awaitTermination(limit.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pause();
            worker.shutdownNow();
        }
    }
}

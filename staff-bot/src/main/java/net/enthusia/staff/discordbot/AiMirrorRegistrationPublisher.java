package net.enthusia.staff.discordbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Disabled-by-default, staging-only receipt registration. The one worker never
 * blocks Discord delivery. Only explicit temporary API errors are retried;
 * a Discord post itself is never repeated.
 *
 * Registrations are memory-only and intentionally do not survive restart. A
 * durable, privacy-reviewed reconciliation design is a separate release gate.
 */
final class AiMirrorRegistrationPublisher implements DiscordChatPostObserver, AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(AiMirrorRegistrationPublisher.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final int QUEUE_CAPACITY = 64;
    private static final int MAX_OUTSTANDING = QUEUE_CAPACITY + 1;
    private static final int MAX_ATTEMPTS = 4;
    private static final int HTTP_OK = 200;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_UNAVAILABLE = 503;
    private static final long MAX_AGE_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final long[] RETRY_DELAYS_MILLIS = {250L, 500L, 1_000L};
    private static final String ERROR_HEADER = "X-Moderation-Mirror-Error";
    private static final String ERROR_PENDING = "canonical_pending";
    private static final String ERROR_CONFLICT = "mirror_conflict";
    private static final String ERROR_TIMEOUT = "timeout";
    private static final String ERROR_STORAGE_UNAVAILABLE = "storage_unavailable";
    private static final ObjectMapper JSON = new ObjectMapper();

    @FunctionalInterface
    interface Poster {
        int post(HttpRequest request) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface DetailedPoster {
        PostResult post(HttpRequest request) throws IOException, InterruptedException;
    }

    record PostResult(int status, String errorCode) {
        PostResult {
            errorCode = errorCode == null ? "" : errorCode;
        }
    }

    private record Transport(DetailedPoster poster) {}

    /** Aggregate-only metrics; no message text, IDs, endpoints, or credentials. */
    record Stats(long confirmed, long pendingResponses, long conflicts,
                 long capacityDrops, long timeoutFailures, long expired,
                 long rejected, long unavailable, long retriesScheduled) {}

    private final AiMirrorPublicationConfig config;
    private final DetailedPoster poster;
    private final ScheduledThreadPoolExecutor worker;
    private final Semaphore slots = new Semaphore(MAX_OUTSTANDING);
    private final Set<Pending> outstanding = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicLong confirmed = new AtomicLong();
    private final AtomicLong pendingResponses = new AtomicLong();
    private final AtomicLong conflicts = new AtomicLong();
    private final AtomicLong capacityDrops = new AtomicLong();
    private final AtomicLong timeoutFailures = new AtomicLong();
    private final AtomicLong expired = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong unavailable = new AtomicLong();
    private final AtomicLong retriesScheduled = new AtomicLong();

    static AiMirrorRegistrationPublisher create(AiMirrorPublicationConfig config) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return withDetailedPoster(config, request -> {
            HttpResponse<Void> response = client.send(
                    request, HttpResponse.BodyHandlers.discarding());
            return new PostResult(response.statusCode(),
                    response.headers().firstValue(ERROR_HEADER).orElse(""));
        });
    }

    static AiMirrorRegistrationPublisher withDetailedPoster(
            AiMirrorPublicationConfig config, DetailedPoster poster) {
        return new AiMirrorRegistrationPublisher(config, new Transport(poster));
    }

    /** Existing integer-only test posters cannot accidentally trigger retries on generic 409/503. */
    AiMirrorRegistrationPublisher(AiMirrorPublicationConfig config, Poster poster) {
        this(config, new Transport(request -> new PostResult(poster.post(request), "")));
    }

    private AiMirrorRegistrationPublisher(
            AiMirrorPublicationConfig config, Transport transport) {
        this.config = Objects.requireNonNull(config, "config");
        this.poster = Objects.requireNonNull(transport, "transport").poster();
        worker = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "StaffBot-AI-Mirror-Staging");
            thread.setDaemon(true);
            return thread;
        });
        // Cancellation does not retain raw chat payloads in the delayed task queue.
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    void resume() {
        if (!closed.get()) {
            accepting.set(true);
        }
    }

    void pause() {
        accepting.set(false);
        epoch.incrementAndGet();
        for (Pending pending : outstanding) {
            pending.finish();
        }
        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(System.Logger.Level.INFO, "ai_mirror_staging_aggregate " + stats());
        }
    }

    Stats stats() {
        return new Stats(confirmed.get(), pendingResponses.get(), conflicts.get(),
                capacityDrops.get(), timeoutFailures.get(), expired.get(),
                rejected.get(), unavailable.get(), retriesScheduled.get());
    }

    @Override
    public void published(DiscordChatPublication publication) {
        if (!eligible(publication)) {
            return;
        }
        if (!slots.tryAcquire()) {
            capacityDrops.incrementAndGet();
            return;
        }
        Pending pending = new Pending(publication, epoch.get());
        outstanding.add(pending);
        pending.schedule(0L);
    }

    private boolean eligible(DiscordChatPublication publication) {
        return publication != null && accepting.get() && !closed.get()
                && publication.guildId() == config.guildId()
                && config.allowedChannels().contains(publication.channelId());
    }

    private boolean active(Pending pending) {
        return !pending.finished.get() && pending.generation == epoch.get()
                && eligible(pending.publication);
    }

    private void attempt(Pending pending) {
        pending.inFlight.set(true);
        try {
            attemptOnce(pending);
        } finally {
            pending.inFlight.set(false);
            pending.releaseIfIdle();
        }
    }

    private void attemptOnce(Pending pending) {
        if (!active(pending)) {
            pending.finish();
            return;
        }
        if (System.nanoTime() - pending.createdAtNanos >= MAX_AGE_NANOS) {
            expired.incrementAndGet();
            pending.finish();
            return;
        }
        pending.attempts++;
        try {
            PostResult result = poster.post(request(config, pending.publication));
            if (!active(pending)) {
                pending.finish();
                return;
            }
            if (result.status() == HTTP_OK) {
                confirmed.incrementAndGet();
                pending.finish();
            } else if (result.status() == HTTP_CONFLICT
                    && ERROR_PENDING.equals(result.errorCode())) {
                pendingResponses.incrementAndGet();
                retryOrFinish(pending);
            } else if (result.status() == HTTP_CONFLICT
                    && ERROR_CONFLICT.equals(result.errorCode())) {
                conflicts.incrementAndGet();
                pending.finish();
            } else if (result.status() == HTTP_UNAVAILABLE
                    && (ERROR_TIMEOUT.equals(result.errorCode())
                    || ERROR_STORAGE_UNAVAILABLE.equals(result.errorCode()))) {
                if (ERROR_TIMEOUT.equals(result.errorCode())) {
                    timeoutFailures.incrementAndGet();
                } else {
                    unavailable.incrementAndGet();
                }
                retryOrFinish(pending);
            } else {
                // All unrecognized 409, 404, unauthorized, schema and redirect
                // statuses are terminal. Never infer identities from text.
                rejected.incrementAndGet();
                pending.finish();
            }
        } catch (HttpTimeoutException timeout) {
            timeoutFailures.incrementAndGet();
            retryOrFinish(pending);
        } catch (IOException failure) {
            unavailable.incrementAndGet();
            retryOrFinish(pending);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            pending.finish();
        } catch (RuntimeException failure) {
            rejected.incrementAndGet();
            pending.finish();
        }
    }

    private void retryOrFinish(Pending pending) {
        if (!active(pending)) {
            pending.finish();
            return;
        }
        if (pending.attempts >= MAX_ATTEMPTS) {
            expired.incrementAndGet();
            pending.finish();
            return;
        }
        long delayMillis = RETRY_DELAYS_MILLIS[pending.attempts - 1];
        long elapsedNanos = System.nanoTime() - pending.createdAtNanos;
        if (elapsedNanos >= MAX_AGE_NANOS
                || TimeUnit.MILLISECONDS.toNanos(delayMillis) >= MAX_AGE_NANOS - elapsedNanos) {
            expired.incrementAndGet();
            pending.finish();
            return;
        }
        retriesScheduled.incrementAndGet();
        pending.schedule(delayMillis);
    }

    static HttpRequest request(AiMirrorPublicationConfig config,
                               DiscordChatPublication publication) throws JsonProcessingException {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("canonical_message_id", publication.canonicalMessageId());
        data.put("canonical_text", publication.canonicalPlainText());
        data.put("platform", "discord");
        data.put("channel_profile", config.channelProfile());
        data.put("scope_id", Long.toString(publication.guildId()));
        data.put("channel_id", Long.toString(publication.channelId()));
        data.put("external_message_id", publication.discordMessageId());
        return HttpRequest.newBuilder(config.endpoint())
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Client-Id", config.clientId())
                .header("Authorization", "Bearer " + config.token())
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(data)))
                .build();
    }

    private final class Pending {
        private final DiscordChatPublication publication;
        private final long generation;
        private final long createdAtNanos = System.nanoTime();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean inFlight = new AtomicBoolean();
        private final AtomicBoolean permitReturned = new AtomicBoolean();
        private volatile ScheduledFuture<?> future;
        private int attempts;

        private Pending(DiscordChatPublication publication, long generation) {
            this.publication = publication;
            this.generation = generation;
        }

        void schedule(long delayMillis) {
            if (!active(this)) {
                finish();
                return;
            }
            try {
                ScheduledFuture<?> scheduled = worker.schedule(
                        () -> attempt(this), delayMillis, TimeUnit.MILLISECONDS);
                future = scheduled;
                if (!active(this)) {
                    scheduled.cancel(false);
                    finish();
                }
            } catch (RejectedExecutionException failure) {
                finish();
            }
        }

        void finish() {
            if (finished.compareAndSet(false, true)) {
                ScheduledFuture<?> scheduled = future;
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
                outstanding.remove(this);
                releaseIfIdle();
            }
        }

        void releaseIfIdle() {
            if (finished.get() && !inFlight.get()
                    && permitReturned.compareAndSet(false, true)) {
                slots.release();
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pause();
            worker.shutdownNow();
        }
    }
}

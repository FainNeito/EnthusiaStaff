package net.enthusia.staff.moderation.api;

import java.util.concurrent.CompletionStage;

/**
 * Read-only, provider-neutral current sanction snapshot.
 *
 * <p>{@link #readAfter(PunishmentLifecycleCursor, int)} pages one deterministic sweep.
 * After {@link PunishmentLifecyclePage#hasMore()} becomes false, consumers must start the
 * next reconciliation sweep from {@link PunishmentLifecycleCursor#beginning()}. Repeated
 * sweeps intentionally provide eventual discovery even when sanctions are committed while
 * an earlier sweep is already in progress.</p>
 */
public interface PunishmentLifecyclePlatform {
    int API_VERSION = 1;
    int MAX_PAGE_SIZE = 100;

    int apiVersion();

    boolean available();

    CompletionStage<PunishmentLifecyclePage> readAfter(PunishmentLifecycleCursor cursor, int limit);
}

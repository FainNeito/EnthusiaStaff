package net.enthusia.staff.moderation.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Scan-local cursor for a deterministic sanction snapshot.
 *
 * <p>Consumers must restart from {@link #beginning()} after a page reports
 * {@code hasMore == false}; this is intentionally not a durable change-feed offset.</p>
 */
public record PunishmentLifecycleCursor(UUID sanctionId) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public PunishmentLifecycleCursor {
        Objects.requireNonNull(sanctionId, "sanctionId");
    }

    public static PunishmentLifecycleCursor beginning() {
        return new PunishmentLifecycleCursor(ZERO_UUID);
    }
}

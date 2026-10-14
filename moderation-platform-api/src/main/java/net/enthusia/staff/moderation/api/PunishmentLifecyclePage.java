package net.enthusia.staff.moderation.api;

import java.util.List;
import java.util.Objects;

public record PunishmentLifecyclePage(
        List<PunishmentLifecycleEvent> events,
        PunishmentLifecycleCursor nextCursor,
        boolean hasMore
) {
    public PunishmentLifecyclePage {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(nextCursor, "nextCursor");
    }
}

package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.InvestigationFlag;

public interface InvestigationFlagStore {
    /** Atomically persists a new flag and immutable creation audit. */
    void create(InvestigationFlag flag);
    List<InvestigationFlag> active(UUID targetId, Instant now, int limit);
    /** Compare-and-set resolution, with audit in the same transaction. False means absent/already resolved. */
    boolean resolve(UUID flagId, UUID actorId, String reason, Instant now);
}

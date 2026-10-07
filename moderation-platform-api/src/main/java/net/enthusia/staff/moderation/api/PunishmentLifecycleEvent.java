package net.enthusia.staff.moderation.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative current projection of one sanction.
 */
public record PunishmentLifecycleEvent(
        UUID sanctionId,
        String caseId,
        UUID subjectId,
        Optional<String> subjectName,
        PunishmentCategory category,
        PunishmentLifecycleSource source,
        String sourcePunishmentId,
        Instant issuedAt,
        Optional<Instant> expiresAt,
        String publicReason,
        Optional<String> actorName,
        boolean active
) {
    public PunishmentLifecycleEvent {
        Objects.requireNonNull(sanctionId, "sanctionId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(subjectId, "subjectId");
        subjectName = Objects.requireNonNull(subjectName, "subjectName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourcePunishmentId, "sourcePunishmentId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(publicReason, "publicReason");
        actorName = Objects.requireNonNull(actorName, "actorName");
        if (caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must be present");
        }
        if (sourcePunishmentId.isBlank()) {
            throw new IllegalArgumentException("sourcePunishmentId must be present");
        }
    }
}

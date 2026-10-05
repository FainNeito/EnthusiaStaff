package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;

/** Staff observation metadata. It grants no enforcement or punishment authority. */
public record InvestigationFlag(UUID flagId, UUID targetId, UUID actorId, String category,
        String reason, Instant createdAt, Instant expiresAt, CaseId caseId) {
    public InvestigationFlag {
        java.util.Objects.requireNonNull(flagId, "flagId");
        java.util.Objects.requireNonNull(targetId, "targetId");
        java.util.Objects.requireNonNull(actorId, "actorId");
        java.util.Objects.requireNonNull(createdAt, "createdAt");
        if (category == null || !category.matches("[a-z][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("category must be a lowercase ID, max 32 characters");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500 || reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("reason must be 1..500 printable characters");
        }
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiry must follow creation");
        }
    }
}

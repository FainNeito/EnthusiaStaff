package net.enthusia.staff.domain.staff;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;

public record VanishRecord(
        UUID staffId,
        StaffRank rank,
        String selectedGameMode,
        Instant updatedAt,
        long revision
) {
    public VanishRecord {
        if (staffId == null || rank == null || updatedAt == null || revision < 0) {
            throw new IllegalArgumentException("vanish record fields are invalid");
        }
        if (selectedGameMode != null && selectedGameMode.isBlank()) {
            throw new IllegalArgumentException("selected game mode must be null or non-blank");
        }
    }

    public VanishRecord(UUID staffId, StaffRank rank, Instant updatedAt, long revision) {
        this(staffId, rank, null, updatedAt, revision);
    }
}

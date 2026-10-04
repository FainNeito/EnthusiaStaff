package net.enthusia.staff.paper.punishment;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;

/**
 * Bounded staff-facing context loaded alongside the punishment GUI.
 *
 * <p>The overview intentionally carries only the information the inventory UI needs. It is not an
 * authority boundary; authoritative punishment preparation and confirmation still re-read current
 * policy and history through the domain workflow.</p>
 */
record PunishmentGuiOverview(
        Instant loadedAt,
        ZoneId timezone,
        long totalHistoryEntries,
        List<ModerationHistoryEntry> recentHistory,
        boolean historyAvailable,
        List<CaseReview> recentCases,
        boolean recentCasesTruncated,
        boolean casesAvailable,
        List<ActiveSanction> activeSanctions,
        boolean sanctionsAvailable,
        int activeReportCount,
        boolean activeReportsTruncated,
        boolean reportsAvailable,
        boolean sensitiveHistory
) {
    PunishmentGuiOverview {
        if (loadedAt == null || timezone == null || totalHistoryEntries < 0
                || recentHistory == null || recentCases == null || activeSanctions == null
                || activeReportCount < 0) {
            throw new IllegalArgumentException("punishment GUI overview fields must be present");
        }
        recentHistory = List.copyOf(recentHistory);
        recentCases = List.copyOf(recentCases);
        activeSanctions = List.copyOf(activeSanctions);
    }

    int exactCaseCount(String reasonId) {
        return (int) recentCases.stream()
                .filter(review -> review.exactReasonId().equals(reasonId))
                .count();
    }

    int familyCaseCount(String family) {
        return (int) recentCases.stream()
                .filter(review -> review.sanctionFamily().equals(family))
                .count();
    }

    int recentWarningCount() {
        return (int) recentCases.stream()
                .filter(review -> review.sanctions().stream().anyMatch(sanction -> sanction.type() == SanctionType.WARNING))
                .count();
    }

    Optional<CaseReview> latestCase() {
        return recentCases.stream()
                .max(java.util.Comparator.comparing(CaseReview::issuedAt));
    }

    static PunishmentGuiOverview unavailable(Instant now, ZoneId timezone, boolean sensitiveHistory) {
        return new PunishmentGuiOverview(
                now,
                timezone,
                0,
                List.of(),
                false,
                List.of(),
                false,
                false,
                List.of(),
                false,
                0,
                false,
                false,
                sensitiveHistory
        );
    }
}

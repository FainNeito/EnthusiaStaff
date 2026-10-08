package net.enthusia.staff.paper.punishment;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.sanction.ActiveSanction;

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
        boolean historyAvailable,
        List<RecentCase> recentCases,
        boolean recentCasesTruncated,
        boolean casesAvailable,
        List<ActiveSanction> activeSanctions,
        boolean sanctionsAvailable,
        int activeReportCount,
        boolean activeReportsTruncated,
        boolean reportsAvailable
) {
    PunishmentGuiOverview {
        if (loadedAt == null || timezone == null || totalHistoryEntries < 0
                || recentCases == null || activeSanctions == null || activeReportCount < 0) {
            throw new IllegalArgumentException("punishment GUI overview fields must be present");
        }
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
                .filter(RecentCase::warning)
                .count();
    }

    Optional<RecentCase> latestCase() {
        return recentCases.stream()
                .max(java.util.Comparator.comparing(RecentCase::issuedAt));
    }

    record RecentCase(
            String exactReasonId,
            String sanctionFamily,
            String publicReason,
            Instant issuedAt,
            boolean warning
    ) {
        RecentCase {
            if (exactReasonId == null || exactReasonId.isBlank()
                    || sanctionFamily == null || sanctionFamily.isBlank()
                    || publicReason == null || publicReason.isBlank()
                    || issuedAt == null) {
                throw new IllegalArgumentException("punishment GUI recent-case fields must be present");
            }
        }
    }
}

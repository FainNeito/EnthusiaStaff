package net.enthusia.staff.persistence;

import java.util.Locale;
import net.enthusia.staff.domain.casefile.CaseReview;

/** Excludes imported console disconnects from moderation views without deleting audit records. */
final class NonPunitiveLegacyKick {
    private NonPunitiveLegacyKick() {
    }

    static String visibleSql(String alias) {
        String reason = "LOWER(COALESCE(" + alias + ".public_reason, ''))";
        return "NOT (COALESCE(" + alias + ".exact_reason_id, '') = 'legacy.litebans.kick'"
                + " AND LOWER(COALESCE(" + alias + ".actor_name, '')) = 'console'"
                + " AND (" + reason + " LIKE '%mainten%'"
                + " OR " + reason + " LIKE '%maintain%'"
                + " OR " + reason + " LIKE '%restart%'"
                + " OR " + reason + " LIKE '%reboot%'"
                + " OR " + reason + " LIKE '%shutting down%'"
                + " OR " + reason + " LIKE '%shutdown%'))";
    }

    static boolean matches(CaseReview review) {
        if (!"legacy.litebans.kick".equals(review.exactReasonId())
                || !"console".equalsIgnoreCase(review.actorName())) {
            return false;
        }
        String reason = review.publicReason().toLowerCase(Locale.ROOT);
        return reason.contains("mainten") || reason.contains("maintain")
                || reason.contains("restart") || reason.contains("reboot")
                || reason.contains("shutting down") || reason.contains("shutdown");
    }
}

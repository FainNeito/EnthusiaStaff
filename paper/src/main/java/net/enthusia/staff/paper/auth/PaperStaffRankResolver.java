package net.enthusia.staff.paper.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.StaffRank;

public final class PaperStaffRankResolver {
    private PaperStaffRankResolver() {
    }

    /**
     * Resolves permanent staff identity first, then falls back to the legacy rank bundles.
     *
     * <p>The identity nodes deliberately carry no authority themselves. This allows LuckPerms
     * groups such as {@code owner}/{@code active-owner} to keep one stable identity while active
     * permissions are moved behind Staff Mode context.</p>
     */
    public static Optional<StaffRank> resolve(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        Optional<StaffRank> identity = resolveIdentity(hasPermission);
        return identity.isPresent() ? identity : resolveLegacyRank(hasPermission);
    }

    public static Optional<StaffRank> resolveIdentity(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (hasPermission.test("enthusiastaff.identity.owner")) {
            return Optional.of(StaffRank.FOUNDER);
        }
        // Developer remains a separate technical role and must not silently inherit moderation authority.
        if (hasPermission.test("enthusiastaff.identity.developer")) {
            return Optional.of(StaffRank.DEVELOPER);
        }
        if (hasPermission.test("enthusiastaff.identity.admin")) {
            return Optional.of(StaffRank.ADMIN);
        }
        if (hasPermission.test("enthusiastaff.identity.mod")) {
            return Optional.of(StaffRank.MOD);
        }
        if (hasPermission.test("enthusiastaff.identity.helper")) {
            return Optional.of(StaffRank.HELPER);
        }
        return Optional.empty();
    }

    public static Optional<StaffRank> resolveLegacyRank(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (hasPermission.test("enthusiastaff.rank.founder")) {
            return Optional.of(StaffRank.FOUNDER);
        }
        if (hasPermission.test("enthusiastaff.rank.developer")) {
            return Optional.of(StaffRank.DEVELOPER);
        }
        if (hasPermission.test("enthusiastaff.rank.admin")) {
            return Optional.of(StaffRank.ADMIN);
        }
        if (hasPermission.test("enthusiastaff.rank.mod")) {
            return Optional.of(StaffRank.MOD);
        }
        if (hasPermission.test("enthusiastaff.rank.helper")) {
            return Optional.of(StaffRank.HELPER);
        }
        return Optional.empty();
    }
}

package net.enthusia.staff.paper.punishment;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;

final class PunishmentGuiCatalog {
    private final ReasonPolicyRepository policies;
    private final AuthorizationPolicy authorization;

    PunishmentGuiCatalog(ReasonPolicyRepository policies, AuthorizationPolicy authorization) {
        if (policies == null || authorization == null) {
            throw new IllegalArgumentException("punishment GUI catalog dependencies must be present");
        }
        this.policies = policies;
        this.authorization = authorization;
    }

    List<PunishmentGuiCategory> categories(Actor actor, String commandName) {
        List<ReasonPolicy> visible = available(actor, commandName);
        return PunishmentGuiCategory.ordered().stream()
                .filter(category -> visible.stream().anyMatch(policy -> category.includes(policy.family())))
                .toList();
    }

    List<ReasonPolicy> reasons(Actor actor, String commandName, String categoryId) {
        PunishmentGuiCategory category = PunishmentGuiCategory.byId(categoryId);
        if (category == null) {
            return List.of();
        }
        return available(actor, commandName).stream()
                .filter(policy -> category.includes(policy.family()))
                .sorted(Comparator.comparing(ReasonPolicy::publicReason).thenComparing(ReasonPolicy::id))
                .toList();
    }

    String categoryId(String family) {
        return PunishmentGuiCategory.forFamily(family).id();
    }

    Optional<ReasonPolicyRepository.ReasonDescriptor> describe(String reasonId) {
        return policies.describe(reasonId);
    }

    Optional<ReasonPolicy> find(String reasonId) {
        return policies.find(reasonId);
    }

    String activeVersion() {
        return policies.activeVersion();
    }

    private List<ReasonPolicy> available(Actor actor, String commandName) {
        boolean mayIssue = authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION);
        boolean mayRequest = authorization.permits(actor, ModerationAction.REQUEST_POLICY_SANCTION);
        if (!mayIssue && !mayRequest) {
            return List.of();
        }
        return policies.all().stream()
                .filter(policy -> !policy.family().startsWith("cheating.polar")
                        && !policy.id().startsWith("cheating.polar."))
                .filter(policy -> visibleAtRank(actor.rank(), policy.requiredRank(), mayRequest))
                .filter(policy -> PunishmentCommandFilter.includes(commandName, policy))
                .toList();
    }

    private static boolean visibleAtRank(StaffRank actorRank, StaffRank requiredRank, boolean mayRequest) {
        if (actorRank == StaffRank.DEVELOPER) {
            return mayRequest;
        }
        if (actorRank == StaffRank.HELPER && requiredRank == StaffRank.MOD) {
            return true;
        }
        return actorRank.atLeast(requiredRank);
    }
}

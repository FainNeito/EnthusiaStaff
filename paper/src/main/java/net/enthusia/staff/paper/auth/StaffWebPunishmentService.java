package net.enthusia.staff.paper.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PreparePunishmentDraftRequest;
import net.enthusia.staff.domain.application.PunishmentDraftConfirmation;
import net.enthusia.staff.domain.application.PunishmentDraftEvaluation;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.auth.StaffTargetHierarchyPolicy;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Private website requests use the same durable configured drafts as the in-game GUI. */
public final class StaffWebPunishmentService {
    private static final int CAPACITY = 1000;
    private static final Duration CONFIRMATION_LIFETIME = Duration.ofMinutes(2);
    private static final Set<SanctionType> SUPPORTED = Set.of(
            SanctionType.WARNING, SanctionType.KICK, SanctionType.MUTE, SanctionType.PUBLIC_MUTE,
            SanctionType.BAN, SanctionType.NETWORK_BAN, SanctionType.NETWORK_IDENTITY_BAN);

    private final Dependencies dependencies;
    private final Function<UUID, Actor> actors;
    private final Function<UUID, Optional<StaffRank>> targetRanks;
    private final Map<UUID, Binding> confirmations = new HashMap<>();

    public record Dependencies(Clock clock, Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows, Supplier<PlayerDirectory> players,
            ReasonPolicyRepository policies, AuthorizationPolicy authorization) {
        public Dependencies {
            java.util.Objects.requireNonNull(clock);
            java.util.Objects.requireNonNull(mode);
            java.util.Objects.requireNonNull(workflows);
            java.util.Objects.requireNonNull(players);
            java.util.Objects.requireNonNull(policies);
            java.util.Objects.requireNonNull(authorization);
        }
    }

    public record Request(UUID actorId, UUID targetId, String sessionBinding,
            String reasonId, String explanation, UUID confirmationId) {
        public Request {
            if (actorId == null || sessionBinding == null || !sessionBinding.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("invalid staff action binding");
            }
        }
    }

    public record Reason(String id, String family, String label) { }
    public record Consequence(String type, String duration) { }
    public record Prepared(UUID confirmationId, UUID targetId, String targetName, String reasonId,
            String reason, String explanation, List<Consequence> consequences, String expiresAt) { }
    public record Status(UUID confirmationId, String state, String caseId, String requestId) { }

    private static final class Binding {
        private final UUID actor;
        private final UUID target;
        private final String session;
        private final Instant expires;
        private Status result;

        Binding(Request request, Instant expires) {
            actor = request.actorId(); target = request.targetId(); session = request.sessionBinding();
            this.expires = expires;
        }

        boolean matches(Request request, Instant now) {
            return actor.equals(request.actorId()) && target.equals(request.targetId())
                    && session.equals(request.sessionBinding()) && now.isBefore(expires);
        }
    }

    public StaffWebPunishmentService(Dependencies dependencies, Function<UUID, Actor> actors,
            Function<UUID, Optional<StaffRank>> targetRanks) {
        this.dependencies = java.util.Objects.requireNonNull(dependencies);
        this.actors = java.util.Objects.requireNonNull(actors);
        this.targetRanks = java.util.Objects.requireNonNull(targetRanks);
    }

    public synchronized Object execute(String operation, Request request) {
        Actor actor = actors.apply(request.actorId());
        if (actor == null || !actor.id().equals(request.actorId()) || actor.rank() == StaffRank.SYSTEM
                || (!dependencies.authorization().permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                && !dependencies.authorization().permits(actor, ModerationAction.REQUEST_POLICY_SANCTION))) {
            throw new SecurityException("current staff punishment authority is required");
        }
        if ("capabilities".equals(operation)) {
            requireNoIntent(request);
            if (request.confirmationId() != null) throw new IllegalArgumentException("unexpected confirmation");
            return Map.of("enabled", dependencies.mode().get() == OperationalMode.ACTIVE
                            && dependencies.workflows().get() != null,
                    "reasons", dependencies.policies().all().stream().filter(StaffWebPunishmentService::supported)
                            .filter(policy -> visibleAtRank(actor, policy))
                            .sorted(Comparator.comparing(ReasonPolicy::family).thenComparing(ReasonPolicy::id))
                            .map(policy -> new Reason(policy.id(), policy.family(), policy.publicReason())).toList());
        }
        if (request.targetId() == null) throw new IllegalArgumentException("a Minecraft target is required");
        PlayerDirectory players = dependencies.players().get();
        if (players == null) throw new IllegalStateException("player directory is unavailable");
        var target = players.find(request.targetId().toString())
                .orElseThrow(() -> new IllegalArgumentException("Minecraft player is not known to the network"));
        if (!target.playerId().equals(request.targetId())) throw new IllegalStateException("player identity mismatch");
        if (!new StaffTargetHierarchyPolicy().permits(actor.rank(), targetRanks.apply(request.targetId()).orElse(null))) {
            throw new SecurityException("staff hierarchy protects this Minecraft player");
        }
        PunishmentDraftWorkflow workflow = dependencies.workflows().get();
        if (workflow == null) throw new IllegalStateException("punishment storage is unavailable");
        return switch (operation) {
            case "prepare" -> prepare(request, actor, workflow,
                    target.currentUsername().orElse(request.targetId().toString()));
            case "confirm", "status" -> confirmOrStatus(operation, request, actor, workflow);
            default -> throw new IllegalArgumentException("unknown Minecraft punishment operation");
        };
    }

    private Prepared prepare(Request request, Actor actor, PunishmentDraftWorkflow workflow, String targetName) {
        Instant now = dependencies.clock().instant();
        confirmations.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expires));
        if (request.confirmationId() != null || confirmations.size() >= CAPACITY) {
            throw new IllegalArgumentException("cannot prepare this punishment");
        }
        if (request.reasonId() == null || !request.reasonId().matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")
                || request.reasonId().length() > 96 || request.explanation() == null
                || request.explanation().length() > 4000) {
            throw new IllegalArgumentException("configured reason and bounded explanation are required");
        }
        ReasonPolicy policy = dependencies.policies().find(request.reasonId())
                .filter(StaffWebPunishmentService::supported)
                .orElseThrow(() -> new IllegalArgumentException("select a supported configured Minecraft reason"));
        PunishmentDraftEvaluation evaluation = workflow.prepare(new PreparePunishmentDraftRequest(
                request.targetId(), actor, policy.id(), request.explanation(),
                policy.publicByDefault() ? CaseVisibility.PUBLIC : CaseVisibility.PRIVATE, "punish"),
                dependencies.mode().get());
        if (evaluation instanceof PunishmentDraftEvaluation.Rejected rejected) {
            throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
        }
        var prepared = (PunishmentDraftEvaluation.Prepared) evaluation;
        if (prepared.assessment().sanctions().stream().anyMatch(spec -> !SUPPORTED.contains(spec.type()))) {
            workflow.discard(prepared.draft().draftId(), actor.id());
            throw new IllegalArgumentException("this consequence requires the in-game workflow");
        }
        Instant expires = now.plus(CONFIRMATION_LIFETIME);
        confirmations.put(prepared.draft().draftId(), new Binding(request, expires));
        return new Prepared(prepared.draft().draftId(), request.targetId(), targetName, policy.id(),
                policy.publicReason(), prepared.draft().internalExplanation(),
                prepared.assessment().sanctions().stream().map(StaffWebPunishmentService::consequence).toList(), expires.toString());
    }

    private Status confirmOrStatus(String operation, Request request, Actor actor, PunishmentDraftWorkflow workflow) {
        requireNoIntent(request);
        Binding binding = confirmations.get(request.confirmationId());
        if (binding == null || !binding.matches(request, dependencies.clock().instant())) {
            throw new IllegalArgumentException("confirmation expired or belongs to another player or session");
        }
        if (binding.result != null) return binding.result;
        if ("status".equals(operation)) return new Status(request.confirmationId(), "PREPARED", null, null);
        var draft = workflow.find(request.confirmationId(), actor.id())
                .orElseThrow(() -> new IllegalArgumentException("punishment draft expired"));
        if (!draft.targetId().equals(request.targetId())
                || dependencies.policies().find(draft.reasonId()).filter(StaffWebPunishmentService::supported).isEmpty()) {
            throw new IllegalArgumentException("prepared punishment no longer matches current policy");
        }
        PunishmentDraftConfirmation confirmed;
        try {
            confirmed = workflow.confirmRouted(request.confirmationId(), actor, dependencies.mode().get());
        } catch (net.enthusia.staff.domain.application.PunishmentDraftCleanupException exception) {
            confirmed = new PunishmentDraftConfirmation.Applied(exception.accepted());
        } catch (net.enthusia.staff.domain.application.PunishmentRequestDraftCleanupException exception) {
            confirmed = new PunishmentDraftConfirmation.Requested(exception.submitted());
        }
        if (confirmed instanceof PunishmentDraftConfirmation.Applied applied) {
            binding.result = new Status(request.confirmationId(), "APPLIED", applied.accepted().caseId().value(), null);
        } else if (confirmed instanceof PunishmentDraftConfirmation.Requested requested) {
            binding.result = new Status(request.confirmationId(), "REQUESTED", null,
                    requested.submitted().request().requestId().toString());
        } else {
            var rejected = (PunishmentDraftConfirmation.Rejected) confirmed;
            throw new IllegalArgumentException(rejected.code() + ": " + rejected.message());
        }
        return binding.result;
    }

    private static void requireNoIntent(Request request) {
        if (request.reasonId() != null || request.explanation() != null) {
            throw new IllegalArgumentException("this operation cannot change the prepared intent");
        }
    }

    private static boolean supported(ReasonPolicy policy) {
        return policy.steps().stream().flatMap(step -> step.sanctions().stream())
                .allMatch(spec -> SUPPORTED.contains(spec.type()));
    }

    private boolean visibleAtRank(Actor actor, ReasonPolicy policy) {
        if (actor.rank() == StaffRank.DEVELOPER) {
            return dependencies.authorization().permits(actor, ModerationAction.REQUEST_POLICY_SANCTION);
        }
        return actor.rank() == StaffRank.HELPER && policy.requiredRank() == StaffRank.MOD
                || actor.rank().atLeast(policy.requiredRank());
    }

    private static Consequence consequence(SanctionSpec spec) {
        return new Consequence(spec.type().name(), switch (spec.length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> durationLabel(spec.length().temporary().orElseThrow());
        });
    }

    private static String durationLabel(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds % 86400 == 0) return unitLabel(seconds / 86400, "day");
        if (seconds % 3600 == 0) return unitLabel(seconds / 3600, "hour");
        if (seconds % 60 == 0) return unitLabel(seconds / 60, "minute");
        return unitLabel(seconds, "second");
    }

    private static String unitLabel(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }
}

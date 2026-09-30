package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.sanction.SanctionLength;

/** Website confirmations reuse the durable D07 service and its current-authority checks. */
final class ModerationActionApiService {
    private static final int MAX_DRAFTS = 1000;
    private final StaffModerationRuntime moderation;
    private final ModerationReadRequestAuthorizer authorizer;
    private final Map<UUID, Binding> drafts = new HashMap<>();

    record Request(String actorId, String guildId, String targetKey, String sessionBinding,
            Optional<IntentInput> intent, Optional<UUID> confirmationId) {
        Request {
            intent = intent == null ? Optional.empty() : intent;
            confirmationId = confirmationId == null ? Optional.empty() : confirmationId;
            if (sessionBinding == null || !sessionBinding.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("session binding is invalid");
            }
        }
    }

    record IntentInput(String type, String duration, String reason, String explanation,
            Optional<DiscordRestrictionTarget> restriction) {
        IntentInput {
            restriction = restriction == null ? Optional.empty() : restriction;
        }

        DiscordPunishmentIntent toIntent() {
            if (type == null) throw new IllegalArgumentException("consequence type is required");
            DiscordConsequenceType consequence = DiscordConsequenceType.valueOf(type);
            boolean instant = consequence == DiscordConsequenceType.WARNING || consequence == DiscordConsequenceType.KICK;
            DiscordDurationParser.Parsed parsed = instant ? null : new DiscordDurationParser().parse(duration, true);
            if (instant && duration != null && !duration.equals("instant")) {
                throw new IllegalArgumentException("instant consequence cannot carry a duration");
            }
            return new DiscordPunishmentIntent(consequence,
                    instant ? SanctionLength.instant() : parsed.length(),
                    !instant && parsed.custom(), false, restriction, reason, explanation, 0, true);
        }
    }

    record Prepared(UUID confirmationId, String targetUserId, DiscordPunishmentIntent intent, Instant expiresAt) {
    }

    record Status(UUID punishmentId, String state, boolean externalApplied, String dmOutcome) {
    }

    private record Binding(String actorId, String guildId, String targetKey, String sessionBinding, Instant expiresAt) {
        boolean matches(Request request) {
            return actorId.equals(request.actorId()) && guildId.equals(request.guildId())
                    && targetKey.equals(request.targetKey()) && sessionBinding.equals(request.sessionBinding());
        }
    }

    ModerationActionApiService(StaffModerationRuntime moderation, ModerationReadRequestAuthorizer authorizer) {
        this.moderation = moderation;
        this.authorizer = authorizer;
    }

    synchronized Object execute(String operation, Request request) {
        ModerationReadContext context = authorizer.authorize(new ModerationReadApiModel.ReadRequest(
                request.actorId(), request.guildId(), request.targetKey(), Optional.empty()));
        if (operation.equals("capabilities")) {
            return Map.of("discordEnabled", moderation.punishmentService().isPresent(),
                    "minecraftEnabled", false, "messageDeletionEnabled", false);
        }
        long target = context.readTarget().userId().orElseThrow(() -> new IllegalArgumentException("select a target"));
        DiscordPunishmentService service = moderation.punishmentService()
                .orElseThrow(() -> new IllegalStateException("Discord enforcement is disabled"));
        return switch (operation) {
            case "prepare" -> prepare(request, context, service, target);
            case "confirm" -> confirm(request, context, service, target);
            case "status" -> status(request, context, service, target);
            default -> throw new IllegalArgumentException("unknown moderation action operation");
        };
    }

    private Prepared prepare(Request request, ModerationReadContext context, DiscordPunishmentService service, long target) {
        drafts.entrySet().removeIf(entry -> !Instant.now().isBefore(entry.getValue().expiresAt()));
        if (request.confirmationId().isPresent() || drafts.size() >= MAX_DRAFTS) {
            throw new IllegalArgumentException("cannot prepare this request");
        }
        DiscordPunishmentIntent intent = request.intent().orElseThrow().toIntent();
        intent.restriction().ifPresent(restriction -> {
            var visible = new ModerationDiscordMessageReader().visibleChannels(context);
            boolean allowed = visible.stream().anyMatch(channel -> restriction.kind() == DiscordRestrictionTarget.Kind.CHANNEL
                    ? channel.id().equals(restriction.snowflake())
                    : channel.categoryId().filter(restriction.snowflake()::equals).isPresent());
            if (!allowed) throw new IllegalArgumentException("restriction scope is not visible to the actor");
        });
        DiscordPunishmentService.Confirmation prepared = service.prepareIssue(
                context.actorId(), context.actorMember().getEffectiveName(), target, intent);
        Instant expiresAt = Instant.now().plusSeconds(120);
        drafts.put(prepared.token(), new Binding(request.actorId(), request.guildId(), request.targetKey(),
                request.sessionBinding(), expiresAt));
        return new Prepared(prepared.token(), prepared.targetUserId(), intent, expiresAt);
    }

    private Status confirm(Request request, ModerationReadContext context, DiscordPunishmentService service, long target) {
        if (request.intent().isPresent()) throw new IllegalArgumentException("confirmation cannot alter intent");
        UUID id = request.confirmationId().orElseThrow();
        Binding binding = drafts.get(id);
        if (binding == null || !binding.matches(request) || !Instant.now().isBefore(binding.expiresAt())) {
            throw new IllegalArgumentException("confirmation expired or belongs to a different session");
        }
        service.confirmWebIssue(context.actorId(), context.actorMember().getEffectiveName(), target, id);
        return status(request, context, service, target);
    }

    private Status status(Request request, ModerationReadContext context, DiscordPunishmentService service, long target) {
        if (request.intent().isPresent()) throw new IllegalArgumentException("status cannot alter intent");
        var stored = service.webPunishment(context.actorId(), context.actorMember().getEffectiveName(), target,
                request.confirmationId().orElseThrow()).orElseThrow(() -> new IllegalArgumentException("punishment not found"));
        var punishment = stored.punishment();
        return new Status(punishment.punishmentId(), punishment.state().name(), punishment.externalApplied(),
                punishment.dmOutcome().name());
    }
}

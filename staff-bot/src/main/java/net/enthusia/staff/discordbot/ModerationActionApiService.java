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
            Optional<IntentInput> intent, Optional<UUID> confirmationId,
            Optional<String> minecraftTarget, Optional<MinecraftIntent> minecraftIntent) {
        Request {
            intent = intent == null ? Optional.empty() : intent;
            confirmationId = confirmationId == null ? Optional.empty() : confirmationId;
            minecraftTarget = minecraftTarget == null ? Optional.empty() : minecraftTarget;
            minecraftIntent = minecraftIntent == null ? Optional.empty() : minecraftIntent;
            minecraftTarget.ifPresent(target -> {
                if (!target.matches("(?:[A-Za-z0-9_]{1,16}|[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12})")) {
                    throw new IllegalArgumentException("invalid Minecraft player name or UUID");
                }
            });
            if (sessionBinding == null || !sessionBinding.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("session binding is invalid");
            }
        }

        Request(String actorId, String guildId, String targetKey, String sessionBinding,
                Optional<IntentInput> intent, Optional<UUID> confirmationId) {
            this(actorId, guildId, targetKey, sessionBinding, intent, confirmationId, Optional.empty(), Optional.empty());
        }
    }

    record MinecraftIntent(String reasonId, String explanation) { }

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
            com.fasterxml.jackson.databind.JsonNode minecraft;
            try {
                minecraft = minecraftRequest("capabilities", request, context);
            } catch (StaffAuthorityClient.UnavailableException | SecurityException exception) {
                minecraft = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                        .put("enabled", false).set("reasons", new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode());
            }
            return Map.of("discordEnabled", moderation.punishmentService().isPresent(),
                    "minecraftEnabled", minecraft.path("enabled").asBoolean(false),
                    "minecraftReasons", minecraft.path("reasons"), "messageDeletionEnabled", false);
        }
        if (request.minecraftTarget().isPresent() || request.minecraftIntent().isPresent()) {
            return minecraftRequest(operation, request, context);
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

    private com.fasterxml.jackson.databind.JsonNode minecraftRequest(
            String operation, Request request, ModerationReadContext context) {
        if (request.intent().isPresent()) throw new IllegalArgumentException("cannot mix Discord and Minecraft intent");
        var actor = moderation.actors().invoker(new net.enthusia.staff.domain.moderation.DiscordUserId(request.actorId()),
                context.actorMember().getEffectiveName());
        UUID target = null;
        if (!"capabilities".equals(operation)) {
            var resolved = moderation.reads().resolveMinecraft(request.minecraftTarget().orElseThrow());
            if (!(resolved instanceof StaffModerationReadService.MinecraftResolution.Resolved found)) {
                throw new IllegalArgumentException("Minecraft player is unknown or ambiguous; use its exact UUID");
            }
            target = found.target().minecraftId().orElseThrow();
        } else if (request.minecraftTarget().isPresent() || request.minecraftIntent().isPresent() || request.confirmationId().isPresent()) {
            throw new IllegalArgumentException("capabilities cannot carry intent");
        }
        if ("prepare".equals(operation)) {
            if (request.confirmationId().isPresent() || request.minecraftIntent().isEmpty()) {
                throw new IllegalArgumentException("configured Minecraft intent is required");
            }
        } else if (request.minecraftIntent().isPresent()) {
            throw new IllegalArgumentException("confirmation cannot change Minecraft intent");
        }
        if (("confirm".equals(operation) || "status".equals(operation)) && request.confirmationId().isEmpty()) {
            throw new IllegalArgumentException("Minecraft confirmation is required");
        }
        var input = new java.util.LinkedHashMap<String, Object>();
        input.put("actorId", actor.id()); input.put("targetId", target);
        input.put("sessionBinding", minecraftSessionBinding(request));
        input.put("confirmationId", request.confirmationId().orElse(null));
        input.put("reasonId", request.minecraftIntent().map(MinecraftIntent::reasonId).orElse(null));
        input.put("explanation", request.minecraftIntent().map(MinecraftIntent::explanation).orElse(null));
        return moderation.authority().punishment(operation, input);
    }

    private static String minecraftSessionBinding(Request request) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    (request.sessionBinding() + ":" + request.guildId() + ":" + request.targetKey())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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

package net.enthusia.staff.paper.punishment;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

final class PunishmentGuiRenderer {
    static final int CONTENT_START = 9;
    static final int CONTENT_SIZE = 36;
    static final int PREVIOUS_SLOT = 45;
    static final int HISTORY_SLOT = 46;
    static final int BACK_SLOT = 47;
    static final int CONFIRM_SLOT = 49;
    static final int CLOSE_SLOT = 51;
    static final int NEXT_SLOT = 53;
    static final int TARGET_REFRESH_SLOT = 49;
    static final int TARGET_CLOSE_SLOT = 51;
    static final int SUMMARY_HISTORY_SLOT = 2;
    static final int VISIBILITY_SLOT = 37;
    static final int NOTE_SLOT = 39;

    private static final int TARGET_SLOT = 4;
    private static final int ACTIVE_SANCTIONS_SLOT = 6;
    private static final int REPORTS_SLOT = 8;
    private static final int HIGH_SEVERITY_THRESHOLD = 75;
    private static final int EXTREME_SEVERITY_THRESHOLD = 90;
    private static final int MODERATE_SEVERITY_THRESHOLD = 40;
    private static final int MAX_HEADER_SANCTIONS = 4;
    private static final int MAX_LADDER_SLOTS = 18;
    private static final int MAX_HISTORY_REASON_LENGTH = 64;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private final PunishmentGuiCatalog catalog;

    PunishmentGuiRenderer(PunishmentGuiCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("punishment GUI catalog must be present");
        }
        this.catalog = catalog;
    }

    Inventory render(PunishmentGuiState state, Actor actor) {
        PunishmentGuiHolder holder = new PunishmentGuiHolder(state);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(state));
        holder.attach(inventory);
        fill(inventory);
        renderHeader(inventory, state);
        if (state instanceof PunishmentGuiState.Categories categories) {
            renderCategories(inventory, categories, actor);
        } else if (state instanceof PunishmentGuiState.Reasons reasons) {
            renderReasons(inventory, reasons, actor);
        } else if (state instanceof PunishmentGuiState.Review review) {
            renderReview(inventory, review, actor);
        } else if (state instanceof PunishmentGuiState.History history) {
            renderHistory(inventory, history);
        }
        return inventory;
    }

    Inventory renderTargetPicker(UUID viewerId, List<Player> targets, int page, String commandName) {
        List<Player> sorted = targets.stream()
                .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<java.util.UUID> ids = sorted.stream().map(Player::getUniqueId).toList();
        PunishmentTargetPickerHolder holder = new PunishmentTargetPickerHolder(
                viewerId, commandName, page, ids
        );
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                Component.text("Punish · Select Player", NamedTextColor.DARK_AQUA)
        );
        holder.attach(inventory);
        fill(inventory);
        renderTargetPickerHeader(inventory, page, sorted.size());
        renderTargetEntries(inventory, sorted, page);
        renderTargetPickerFooter(inventory, page, sorted.size());
        return inventory;
    }

    private void renderCategories(Inventory inventory, PunishmentGuiState.Categories state, Actor actor) {
        List<String> categories = catalog.categories(actor, state.commandName());
        int offset = state.page() * CONTENT_SIZE;
        for (int index = 0; index < CONTENT_SIZE && offset + index < categories.size(); index++) {
            String family = categories.get(offset + index);
            int reasonCount = catalog.reasons(actor, state.commandName(), family).size();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(
                    reasonCount + " available reason" + (reasonCount == 1 ? "" : "s"),
                    NamedTextColor.GRAY
            ));
            if (state.overview().casesAvailable()) {
                lore.add(Component.text(
                        "Recent family cases: " + state.overview().familyCaseCount(family)
                                + (state.overview().recentCasesTruncated() ? "+" : ""),
                        NamedTextColor.GREEN
                ));
            }
            lore.add(Component.text("Click to choose an exact reason", NamedTextColor.YELLOW));
            inventory.setItem(CONTENT_START + index, item(
                    familyMaterial(family),
                    humanize(family),
                    familyColor(family),
                    lore
            ));
        }
        if (categories.isEmpty()) {
            emptyState(inventory, "No punishment categories available",
                    "Your current rank and route have no selectable configured reasons.");
        }
        pageControls(inventory, state.page(), categories.size());
        footerHistory(inventory);
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED));
    }

    private void renderReasons(Inventory inventory, PunishmentGuiState.Reasons state, Actor actor) {
        List<ReasonPolicy> reasons = catalog.reasons(actor, state.commandName(), state.family());
        int offset = state.page() * CONTENT_SIZE;
        for (int index = 0; index < CONTENT_SIZE && offset + index < reasons.size(); index++) {
            ReasonPolicy policy = reasons.get(offset + index);
            inventory.setItem(
                    CONTENT_START + index,
                    item(reasonMaterial(policy), policy.publicReason(), reasonNameColor(policy), reasonLore(policy, state.overview()))
            );
        }
        if (reasons.isEmpty()) {
            emptyState(inventory, "No exact reasons available", "Return to categories and select another group.");
        }
        pageControls(inventory, state.page(), reasons.size());
        footerHistory(inventory);
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "Back · Categories", NamedTextColor.AQUA));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED));
    }

    private void renderReview(Inventory inventory, PunishmentGuiState.Review state, Actor actor) {
        PunishmentDraft draft = state.draft();
        Optional<PunishmentAssessment> assessment = state.assessment();
        ReasonPolicy policy = assessment.map(PunishmentAssessment::policy).orElse(null);

        inventory.setItem(10, reasonReviewItem(draft, policy));
        inventory.setItem(12, escalationItem(state));
        inventory.setItem(14, recommendationItem(state));
        inventory.setItem(16, safetyItem(state));
        assessment.ifPresent(value -> renderLadder(inventory, value));

        inventory.setItem(VISIBILITY_SLOT, visibilityItem(draft));
        inventory.setItem(NOTE_SLOT, noteItem(draft));
        inventory.setItem(41, historySummaryItem(state));
        inventory.setItem(43, authorityItem(actor, policy));

        inventory.setItem(BACK_SLOT, button(Material.ARROW, "Back · Reasons", NamedTextColor.AQUA));
        footerHistory(inventory);
        inventory.setItem(CONFIRM_SLOT, item(
                Material.LIME_CONCRETE,
                "Confirm Punishment",
                NamedTextColor.GREEN,
                List.of(
                        Component.text("Creates the case or durable approval request.", NamedTextColor.WHITE),
                        yesNoLine("Policy rechecked on confirm", true),
                        yesNoLine("Rank rechecked on confirm", true),
                        Component.text("No click before this point applies punishment.", NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(CLOSE_SLOT, item(
                Material.BARRIER,
                "Save & Close",
                NamedTextColor.RED,
                List.of(Component.text("The draft remains resumable for its configured lifetime.", NamedTextColor.GRAY))
        ));
    }

    private void renderHistory(Inventory inventory, PunishmentGuiState.History state) {
        List<ModerationHistoryEntry> entries = state.history().entries();
        for (int index = 0; index < entries.size() && index < CONTENT_SIZE; index++) {
            inventory.setItem(
                    CONTENT_START + index,
                    historyEntryItem(entries.get(index), state.overview(), state.sensitiveHistory())
            );
        }
        if (entries.isEmpty()) {
            emptyState(inventory, "No punishment history", "No moderation timeline entries are recorded for this player.");
        }
        int page = state.history().page();
        int totalPages = state.history().totalPages();
        if (page > 1) {
            inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "Previous History Page", NamedTextColor.AQUA));
        }
        if (page < totalPages) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "Next History Page", NamedTextColor.AQUA));
        }
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "Back", NamedTextColor.AQUA));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED));
    }

    private void renderHeader(Inventory inventory, PunishmentGuiState state) {
        inventory.setItem(0, workflowItem(state));
        inventory.setItem(SUMMARY_HISTORY_SLOT, historyHeaderItem(state));
        inventory.setItem(TARGET_SLOT, targetItem(state));
        inventory.setItem(ACTIVE_SANCTIONS_SLOT, activeSanctionsItem(state.overview()));
        inventory.setItem(REPORTS_SLOT, reportsItem(state.overview()));
    }

    private static ItemStack workflowItem(PunishmentGuiState state) {
        String phase = state instanceof PunishmentGuiState.Categories ? "Categories"
                : state instanceof PunishmentGuiState.Reasons ? "Exact Reason"
                : state instanceof PunishmentGuiState.Review ? "Review & Confirm"
                : "History";
        return item(
                Material.NETHER_STAR,
                "Punishment Workflow",
                NamedTextColor.GOLD,
                List.of(
                        Component.text("Route: /" + state.commandName(), NamedTextColor.GRAY),
                        Component.text("Phase: " + phase, NamedTextColor.WHITE),
                        Component.text("Authoritative policy is never decided by the GUI.", NamedTextColor.DARK_GRAY)
                )
        );
    }

    private static ItemStack historyHeaderItem(PunishmentGuiState state) {
        PunishmentGuiOverview overview = state.overview();
        List<Component> lore = new ArrayList<>();
        if (!overview.historyAvailable()) {
            lore.add(Component.text("Timeline: unavailable", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Timeline entries: " + overview.totalHistoryEntries(), NamedTextColor.WHITE));
        }
        if (!overview.casesAvailable()) {
            lore.add(Component.text("Recent cases: unavailable", NamedTextColor.RED));
        } else {
            lore.add(Component.text(
                    "Recent cases loaded: " + overview.recentCases().size()
                            + (overview.recentCasesTruncated() ? "+" : ""),
                    NamedTextColor.GRAY
            ));
            lore.add(Component.text("Recent warnings: " + overview.recentWarningCount(), NamedTextColor.GRAY));
            overview.latestCase().ifPresent(review -> lore.add(Component.text(
                    "Last: " + compact(review.publicReason(), 42),
                    NamedTextColor.GRAY
            )));
        }
        lore.add(Component.text("Click to open the history browser", NamedTextColor.YELLOW));
        return item(Material.BOOK, "Punishment History", NamedTextColor.AQUA, lore);
    }

    private static ItemStack targetItem(PunishmentGuiState state) {
        boolean online = Bukkit.getPlayer(state.target().playerId()) != null;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(state.target().playerId().toString(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Platform: " + state.target().platform(), NamedTextColor.GRAY));
        lore.add(booleanLine("Online", online));
        lore.add(Component.text("Last seen: " + formatInstant(state.target().lastSeenAt(), state.overview()),
                NamedTextColor.GRAY));
        return playerHead(
                state.target().playerId(),
                targetName(state.target()),
                NamedTextColor.WHITE,
                lore
        );
    }

    private static ItemStack activeSanctionsItem(PunishmentGuiOverview overview) {
        List<Component> lore = new ArrayList<>();
        if (!overview.sanctionsAvailable()) {
            lore.add(Component.text("Active sanctions: unavailable", NamedTextColor.RED));
            return item(Material.IRON_BARS, "Active Sanctions", NamedTextColor.RED, lore);
        }
        if (overview.activeSanctions().isEmpty()) {
            lore.add(Component.text("None", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Count: " + overview.activeSanctions().size(), NamedTextColor.RED));
            overview.activeSanctions().stream().limit(MAX_HEADER_SANCTIONS)
                    .forEach(sanction -> lore.add(activeSanctionLine(sanction, overview)));
            if (overview.activeSanctions().size() > MAX_HEADER_SANCTIONS) {
                lore.add(Component.text("… +" + (overview.activeSanctions().size() - MAX_HEADER_SANCTIONS) + " more",
                        NamedTextColor.DARK_GRAY));
            }
        }
        return item(Material.IRON_BARS, "Active Sanctions",
                overview.activeSanctions().isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED, lore);
    }

    private static ItemStack reportsItem(PunishmentGuiOverview overview) {
        List<Component> lore = new ArrayList<>();
        if (!overview.reportsAvailable()) {
            lore.add(Component.text("Open reports: unavailable", NamedTextColor.RED));
        } else {
            String count = overview.activeReportCount() + (overview.activeReportsTruncated() ? "+" : "");
            NamedTextColor color = overview.activeReportCount() == 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD;
            lore.add(Component.text("Open reports: " + count, color));
            lore.add(booleanLine("Has open reports", overview.activeReportCount() > 0));
        }
        lore.add(Component.text("Use /reports for report evidence and workflow.", NamedTextColor.DARK_GRAY));
        return item(Material.PAPER, "Report Context", NamedTextColor.GOLD, lore);
    }

    private static List<Component> reasonLore(ReasonPolicy policy, PunishmentGuiOverview overview) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(policy.id(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(
                "Severity " + policy.severity() + "/100 · Required " + policy.requiredRank(),
                severityColor(policy.severity())
        ));
        if (overview.casesAvailable()) {
            lore.add(Component.text(
                    "Recent exact: " + overview.exactCaseCount(policy.id())
                            + " · family: " + overview.familyCaseCount(policy.family())
                            + (overview.recentCasesTruncated() ? "+" : ""),
                    NamedTextColor.GRAY
            ));
        }
        lore.add(booleanLine("Decay enabled", policy.decayEnabled()));
        lore.add(Component.text("Ladder: " + policy.steps().size() + " step" + (policy.steps().size() == 1 ? "" : "s"),
                NamedTextColor.GRAY));
        if (!policy.examples().isEmpty()) {
            lore.add(Component.text("Example: " + compact(policy.examples().getFirst(), 54), NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.text("Click to calculate this player's current step", NamedTextColor.YELLOW));
        return List.copyOf(lore);
    }

    private ItemStack reasonReviewItem(PunishmentDraft draft, ReasonPolicy policy) {
        var descriptor = catalog.describe(draft.reasonId());
        List<Component> lore = new ArrayList<>(PunishmentReasonPresentation.lore(draft.reasonId(), descriptor));
        if (policy != null) {
            lore.add(Component.text(
                    "Family: " + humanize(policy.family()) + " · Severity " + policy.severity() + "/100",
                    severityColor(policy.severity())
            ));
            lore.add(booleanLine("Decay enabled", policy.decayEnabled()));
            lore.add(booleanLine("Public by default", policy.publicByDefault()));
            lore.add(booleanLine("Reportable", policy.reportable()));
        }
        return item(
                Material.WRITABLE_BOOK,
                PunishmentReasonPresentation.name(descriptor),
                policy == null ? NamedTextColor.WHITE : reasonNameColor(policy),
                lore
        );
    }

    private static ItemStack escalationItem(PunishmentGuiState.Review state) {
        List<Component> lore = new ArrayList<>();
        PunishmentAssessment assessment = state.assessment().orElse(null);
        if (assessment == null) {
            lore.add(Component.text("Detailed escalation context is unavailable for this resumed draft.",
                    NamedTextColor.YELLOW));
            return item(Material.PRISMARINE_CRYSTALS, "Escalation Context", NamedTextColor.AQUA, lore);
        }
        int contributing = (int) assessment.escalation().contributions().stream()
                .filter(value -> value.effective() > 0)
                .count();
        int decayed = (int) assessment.escalation().contributions().stream()
                .filter(value -> value.decayedBy() > 0)
                .count();
        lore.add(Component.text("Contributing history: " + contributing, NamedTextColor.GREEN));
        lore.add(Component.text("Decayed history: " + decayed, NamedTextColor.AQUA));
        lore.add(Component.text("Recency bonus: +" + assessment.escalation().recencyBonus(), NamedTextColor.GOLD));
        lore.add(Component.text(
                "Raw step " + (assessment.escalation().rawOrdinal() + 1)
                        + " → effective step " + (assessment.escalation().effectiveOrdinal() + 1),
                NamedTextColor.GRAY
        ));
        lore.add(Component.text(
                "Recent exact cases: " + state.overview().exactCaseCount(assessment.policy().id())
                        + " · family: " + state.overview().familyCaseCount(assessment.policy().family()),
                NamedTextColor.GRAY
        ));
        return item(Material.PRISMARINE_CRYSTALS, "Escalation Context", NamedTextColor.AQUA, lore);
    }

    private static ItemStack recommendationItem(PunishmentGuiState.Review state) {
        PunishmentDraft draft = state.draft();
        int total = state.assessment().map(value -> value.policy().steps().size()).orElse(0);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(
                "Step " + (draft.expectation().stepOrdinal() + 1) + (total > 0 ? "/" + total : ""),
                NamedTextColor.GOLD
        ));
        lore.add(Component.text(describe(draft.expectation().sanctions()), NamedTextColor.WHITE));
        lore.add(Component.text("Policy version: " + draft.expectation().configurationVersion(),
                NamedTextColor.DARK_GRAY));
        lore.add(Component.text(
                "Gold = recommended · green = prior · gray = future · red = permanent",
                NamedTextColor.GRAY
        ));
        return item(Material.GOLD_INGOT, "Authoritative Recommendation", NamedTextColor.GOLD, lore);
    }

    private static ItemStack safetyItem(PunishmentGuiState.Review state) {
        return item(
                Material.CLOCK,
                "Draft Safety",
                NamedTextColor.AQUA,
                List.of(
                        Component.text("Expires: " + formatInstant(state.draft().expiresAt(), state.overview()),
                                NamedTextColor.GRAY),
                        yesNoLine("Survives logout/restart/server switch", true),
                        yesNoLine("Revalidates changed ladder before commit", true),
                        Component.text("A stale recommendation cannot silently apply.", NamedTextColor.DARK_GRAY)
                )
        );
    }

    private static ItemStack visibilityItem(PunishmentDraft draft) {
        boolean publicCase = draft.visibility() == net.enthusia.staff.domain.casefile.CaseVisibility.PUBLIC;
        return item(
                publicCase ? Material.LIME_DYE : Material.GRAY_DYE,
                "Visibility",
                publicCase ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(
                        booleanLine("Public", publicCase),
                        Component.text(
                                publicCase ? "Click to make this case private." : "Click to make this case public.",
                                NamedTextColor.YELLOW
                        )
                )
        );
    }

    private static ItemStack noteItem(PunishmentDraft draft) {
        List<Component> lore = new ArrayList<>();
        if (draft.internalExplanation().isBlank()) {
            lore.add(Component.text("No internal explanation entered.", NamedTextColor.RED));
        } else {
            lore.addAll(wrapped(draft.internalExplanation(), 48, 3, NamedTextColor.GRAY));
        }
        lore.add(Component.text("Click to edit the private internal explanation.", NamedTextColor.YELLOW));
        return item(Material.NAME_TAG, "Internal Explanation", NamedTextColor.AQUA, lore);
    }

    private static ItemStack historySummaryItem(PunishmentGuiState.Review state) {
        List<Component> lore = new ArrayList<>();
        if (state.overview().historyAvailable()) {
            lore.add(Component.text("Timeline entries: " + state.overview().totalHistoryEntries(), NamedTextColor.WHITE));
        } else {
            lore.add(Component.text("Timeline unavailable", NamedTextColor.RED));
        }
        state.overview().latestCase().ifPresent(review -> {
            lore.add(Component.text("Last case: " + compact(review.publicReason(), 48), NamedTextColor.GRAY));
            lore.add(Component.text("Issued: " + formatInstant(review.issuedAt(), state.overview()), NamedTextColor.DARK_GRAY));
        });
        lore.add(Component.text("Use the History button below for the full browser.", NamedTextColor.YELLOW));
        return item(Material.BOOK, "Relevant History", NamedTextColor.AQUA, lore);
    }

    private static ItemStack authorityItem(Actor actor, ReasonPolicy policy) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Your rank: " + actor.rank(), NamedTextColor.WHITE));
        if (policy != null) {
            lore.add(Component.text("Required rank: " + policy.requiredRank(), NamedTextColor.GRAY));
            lore.add(booleanLine("Meets direct rank threshold", actor.rank().atLeast(policy.requiredRank())));
        }
        lore.add(Component.text("Helper/request-only outcomes still route through approval.", NamedTextColor.DARK_GRAY));
        return item(Material.SHIELD, "Authority", NamedTextColor.LIGHT_PURPLE, lore);
    }

    private static void renderLadder(Inventory inventory, PunishmentAssessment assessment) {
        List<PunishmentStep> steps = assessment.policy().steps();
        int selected = assessment.escalation().selectedStep().ordinal();
        int shown = Math.min(steps.size(), steps.size() > MAX_LADDER_SLOTS ? MAX_LADDER_SLOTS - 1 : MAX_LADDER_SLOTS);
        for (int index = 0; index < shown; index++) {
            int slot = 18 + index;
            PunishmentStep step = steps.get(index);
            inventory.setItem(slot, ladderItem(step, index, selected));
        }
        if (steps.size() > MAX_LADDER_SLOTS) {
            inventory.setItem(35, item(
                    Material.MAP,
                    "Additional Ladder Steps",
                    NamedTextColor.GRAY,
                    List.of(Component.text(
                            (steps.size() - MAX_LADDER_SLOTS) + " additional configured steps are not shown here.",
                            NamedTextColor.GRAY
                    ))
            ));
        }
    }

    private static ItemStack ladderItem(PunishmentStep step, int ordinal, int selected) {
        boolean current = ordinal == selected;
        boolean prior = ordinal < selected;
        boolean permanent = step.sanctions().stream().anyMatch(spec -> spec.length().isPermanent());
        Material material = current ? Material.YELLOW_CONCRETE
                : prior ? Material.LIME_CONCRETE
                : permanent ? Material.RED_CONCRETE
                : Material.GRAY_CONCRETE;
        NamedTextColor color = current ? NamedTextColor.GOLD
                : prior ? NamedTextColor.GREEN
                : permanent ? NamedTextColor.RED
                : NamedTextColor.GRAY;
        String prefix = current ? "▶ " : prior ? "✓ " : "";
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(step.label(), NamedTextColor.WHITE));
        lore.add(Component.text(describe(step.sanctions()), permanent ? NamedTextColor.RED : NamedTextColor.GRAY));
        lore.add(booleanLine("Permanent", permanent));
        lore.add(Component.text(
                current ? "RECOMMENDED NOW" : prior ? "Previous ladder step" : "Future ladder step",
                color
        ));
        return item(material, prefix + "Step " + (ordinal + 1), color, lore);
    }

    private static ItemStack historyEntryItem(
            ModerationHistoryEntry entry,
            PunishmentGuiOverview overview,
            boolean sensitiveHistory
    ) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(formatInstant(entry.occurredAt(), overview), NamedTextColor.DARK_GRAY));
        entry.punishmentType().ifPresent(type ->
                lore.add(Component.text("Type: " + humanize(type), NamedTextColor.GRAY)));
        lore.add(Component.text("Status: " + humanize(entry.status()), statusColor(entry.status())));
        if (!entry.publicReason().isBlank()) {
            lore.add(Component.text("Reason: " + compact(entry.publicReason(), MAX_HISTORY_REASON_LENGTH),
                    NamedTextColor.WHITE));
        }
        entry.caseId().ifPresent(caseId ->
                lore.add(Component.text("Case: " + caseId.value(), NamedTextColor.DARK_GRAY)));
        if (sensitiveHistory) {
            entry.actorName().ifPresent(name ->
                    lore.add(Component.text("Actor: " + name, NamedTextColor.DARK_GRAY)));
            entry.sensitiveReason().ifPresent(reason ->
                    lore.add(Component.text("Internal: " + compact(reason, 56), NamedTextColor.DARK_GRAY)));
        }
        return item(historyMaterial(entry), humanize(entry.eventType().name()), statusColor(entry.status()), lore);
    }

    private static Material historyMaterial(ModerationHistoryEntry entry) {
        if (entry.punishmentType().isPresent()) {
            String type = entry.punishmentType().orElseThrow().toUpperCase(Locale.ROOT);
            if (type.contains("BAN")) {
                return Material.BARRIER;
            }
            if (type.contains("MUTE")) {
                return Material.OAK_SIGN;
            }
            if (type.contains("WARNING")) {
                return Material.BELL;
            }
        }
        return switch (entry.eventType()) {
            case CASE_CREATED -> Material.BOOK;
            case SANCTION_REVOKED, SANCTION_OVERTURNED -> Material.REDSTONE;
            case SANCTION_EXPIRED, SANCTION_ENDED_EARLY -> Material.CLOCK;
            default -> Material.PAPER;
        };
    }

    private static void renderTargetPickerHeader(Inventory inventory, int page, int total) {
        inventory.setItem(0, item(
                Material.NETHER_STAR,
                "Punishment Workflow",
                NamedTextColor.GOLD,
                List.of(Component.text("Select a player to open the full ladder workflow.", NamedTextColor.GRAY))
        ));
        inventory.setItem(4, item(
                Material.COMPASS,
                "Select Player",
                NamedTextColor.AQUA,
                List.of(
                        Component.text(total + " visible online player" + (total == 1 ? "" : "s"), NamedTextColor.WHITE),
                        Component.text("Page " + (page + 1), NamedTextColor.GRAY),
                        Component.text("Offline or historical player? Use /punish <player>.", NamedTextColor.DARK_GRAY)
                )
        ));
    }

    private static void renderTargetEntries(Inventory inventory, List<Player> targets, int page) {
        int offset = page * CONTENT_SIZE;
        for (int index = 0; index < CONTENT_SIZE && offset + index < targets.size(); index++) {
            Player target = targets.get(offset + index);
            inventory.setItem(CONTENT_START + index, playerHead(
                    target.getUniqueId(),
                    target.getName(),
                    NamedTextColor.WHITE,
                    List.of(
                            booleanLine("Online", true),
                            Component.text(target.getUniqueId().toString(), NamedTextColor.DARK_GRAY),
                            Component.text("Click to open punishment categories.", NamedTextColor.YELLOW)
                    )
            ));
        }
        if (targets.isEmpty()) {
            emptyState(inventory, "No visible online targets", "Use /punish <player> for an offline or historical player.");
        }
    }

    private static void renderTargetPickerFooter(Inventory inventory, int page, int total) {
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "Previous Page", NamedTextColor.AQUA));
        }
        if ((page + 1) * CONTENT_SIZE < total) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "Next Page", NamedTextColor.AQUA));
        }
        inventory.setItem(TARGET_REFRESH_SLOT, button(Material.CLOCK, "Refresh Players", NamedTextColor.YELLOW));
        inventory.setItem(TARGET_CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED));
    }

    private static void footerHistory(Inventory inventory) {
        inventory.setItem(HISTORY_SLOT, item(
                Material.BOOK,
                "History",
                NamedTextColor.AQUA,
                List.of(Component.text("Open this player's punishment-history browser.", NamedTextColor.YELLOW))
        ));
    }

    private static void pageControls(Inventory inventory, int page, int entries) {
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "Previous Page", NamedTextColor.AQUA));
        }
        if ((page + 1) * CONTENT_SIZE < entries) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "Next Page", NamedTextColor.AQUA));
        }
    }

    private static void emptyState(Inventory inventory, String title, String detail) {
        inventory.setItem(31, item(
                Material.GRAY_DYE,
                title,
                NamedTextColor.GRAY,
                List.of(Component.text(detail, NamedTextColor.DARK_GRAY))
        ));
    }

    private static void fill(Inventory inventory) {
        ItemStack content = item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slot < CONTENT_START || slot >= 45 ? frame : content);
        }
    }

    private static Component title(PunishmentGuiState state) {
        String target = targetName(state.target());
        if (state instanceof PunishmentGuiState.Reasons reasons) {
            return Component.text("Punish " + target + " · " + humanize(reasons.family()), NamedTextColor.DARK_AQUA);
        }
        if (state instanceof PunishmentGuiState.Review) {
            return Component.text("Review Punishment · " + target, NamedTextColor.DARK_AQUA);
        }
        if (state instanceof PunishmentGuiState.History history) {
            return Component.text(
                    "History " + target + " · " + history.history().page() + "/" + Math.max(1, history.history().totalPages()),
                    NamedTextColor.DARK_AQUA
            );
        }
        return Component.text("Punish " + target + " · Categories", NamedTextColor.DARK_AQUA);
    }

    private static Material familyMaterial(String family) {
        String root = family.split("\\.", 2)[0];
        return switch (root) {
            case "hate", "harassment" -> Material.REDSTONE;
            case "safety", "privacy" -> Material.SHIELD;
            case "spam", "language" -> Material.PAPER;
            case "content", "identity" -> Material.PAINTING;
            case "advertising", "market", "reputation" -> Material.EMERALD;
            case "account", "evasion" -> Material.ENDER_EYE;
            case "exploit", "mechanics", "cheating" -> Material.DIAMOND_PICKAXE;
            case "reports", "staff", "dishonesty", "complicity" -> Material.BOOK;
            default -> Material.MAP;
        };
    }

    private static NamedTextColor familyColor(String family) {
        String root = family.split("\\.", 2)[0];
        return switch (root) {
            case "hate", "harassment", "safety" -> NamedTextColor.RED;
            case "exploit", "mechanics", "cheating" -> NamedTextColor.GOLD;
            case "account", "evasion" -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.AQUA;
        };
    }

    private static Material reasonMaterial(ReasonPolicy policy) {
        if (policy.severity() >= EXTREME_SEVERITY_THRESHOLD) {
            return Material.RED_CONCRETE;
        }
        if (policy.severity() >= HIGH_SEVERITY_THRESHOLD) {
            return Material.ORANGE_CONCRETE;
        }
        if (policy.severity() >= MODERATE_SEVERITY_THRESHOLD) {
            return Material.YELLOW_CONCRETE;
        }
        return Material.LIGHT_BLUE_CONCRETE;
    }

    private static NamedTextColor reasonNameColor(ReasonPolicy policy) {
        if (policy.severity() >= EXTREME_SEVERITY_THRESHOLD) {
            return NamedTextColor.DARK_RED;
        }
        if (policy.severity() >= HIGH_SEVERITY_THRESHOLD) {
            return NamedTextColor.RED;
        }
        if (policy.severity() >= MODERATE_SEVERITY_THRESHOLD) {
            return NamedTextColor.GOLD;
        }
        return NamedTextColor.AQUA;
    }

    private static NamedTextColor severityColor(int severity) {
        if (severity >= EXTREME_SEVERITY_THRESHOLD) {
            return NamedTextColor.DARK_RED;
        }
        if (severity >= HIGH_SEVERITY_THRESHOLD) {
            return NamedTextColor.RED;
        }
        if (severity >= MODERATE_SEVERITY_THRESHOLD) {
            return NamedTextColor.GOLD;
        }
        return NamedTextColor.AQUA;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color) {
        return item(material, name, color, List.of());
    }

    private static ItemStack playerHead(
            UUID playerId,
            String name,
            NamedTextColor color,
            List<Component> lore
    ) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
            meta.displayName(Component.text(name, color));
            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }
        return item(Material.PLAYER_HEAD, name, color, lore);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component booleanLine(String label, boolean value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value ? "YES" : "NO", value ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private static Component yesNoLine(String label, boolean value) {
        return booleanLine(label, value);
    }

    private static Component activeSanctionLine(ActiveSanction sanction, PunishmentGuiOverview overview) {
        Component line = Component.text("• " + humanize(sanction.type().name()), NamedTextColor.RED);
        if (sanction.permanent()) {
            return line.append(Component.text(" · PERMANENT", NamedTextColor.RED));
        }
        return line.append(Component.text(
                " · expires " + formatInstant(sanction.expiresAt().orElseThrow(), overview),
                NamedTextColor.GOLD
        ));
    }

    private static NamedTextColor statusColor(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (normalized.contains("active") || normalized.contains("applied")
                || normalized.contains("complete") || normalized.contains("approved")) {
            return NamedTextColor.GREEN;
        }
        if (normalized.contains("fail") || normalized.contains("reject") || normalized.contains("denied")
                || normalized.contains("overturn") || normalized.contains("revoke")) {
            return NamedTextColor.RED;
        }
        if (normalized.contains("expired") || normalized.contains("ended")) {
            return NamedTextColor.GRAY;
        }
        return NamedTextColor.GOLD;
    }

    static String describe(List<SanctionSpec> sanctions) {
        return sanctions.stream().map(PunishmentGuiRenderer::describe)
                .reduce((left, right) -> left + " + " + right)
                .orElse("No sanction");
    }

    private static String describe(SanctionSpec sanction) {
        SanctionLength length = sanction.length();
        if (length.isInstant()) {
            return humanize(sanction.type().name());
        }
        if (length.isPermanent()) {
            return "Permanent " + humanize(sanction.type().name());
        }
        return duration(length.temporary().orElseThrow()) + " " + humanize(sanction.type().name());
    }

    private static String duration(Duration duration) {
        if (duration.toDaysPart() > 0 && duration.toHoursPart() == 0
                && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toDays() + "d";
        }
        if (duration.toHours() > 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toHours() + "h";
        }
        return duration.toString();
    }

    private static String targetName(net.enthusia.staff.domain.player.PlayerIdentity target) {
        return target.currentUsername().orElse(target.playerId().toString());
    }

    private static String formatInstant(Instant instant, PunishmentGuiOverview overview) {
        return DATE.withZone(overview.timezone()).format(instant);
    }

    private static List<Component> wrapped(String value, int width, int maxLines, NamedTextColor color) {
        String compact = value.replace('\n', ' ').replace('\r', ' ').trim();
        List<Component> lines = new ArrayList<>();
        for (int start = 0; start < compact.length() && lines.size() < maxLines; start += width) {
            lines.add(Component.text(compact.substring(start, Math.min(compact.length(), start + width)), color));
        }
        if (compact.length() > width * maxLines) {
            lines.add(Component.text("…", NamedTextColor.DARK_GRAY));
        }
        return List.copyOf(lines);
    }

    private static String compact(String value, int maxLength) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String humanize(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('.', ' ').replace('-', ' ');
        if (normalized.isBlank()) {
            return "Unknown";
        }
        String[] words = normalized.split(" +");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

}

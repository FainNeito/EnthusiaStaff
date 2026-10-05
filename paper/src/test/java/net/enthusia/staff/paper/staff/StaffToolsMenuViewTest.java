package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class StaffToolsMenuViewTest {
    private static final UUID VIEWER_ID = UUID.fromString("66000000-0000-0000-0000-000000000001");

    @Test
    void rootViewPreservesOnlyDistinctActionTools() {
        StaffToolsMenuView.Root root = StaffToolsMenuView.root(
                VIEWER_ID,
                List.of(StaffToolDefinition.RANDOM_TELEPORT, StaffToolDefinition.REPORTS)
        );

        assertEquals(StaffToolDefinition.RANDOM_TELEPORT, root.toolAt(0));
        assertNull(root.toolAt(1));
        assertEquals(StaffToolDefinition.REPORTS, root.toolAt(3));
        assertNull(root.toolAt(2));
        assertThrows(IllegalArgumentException.class, () -> StaffToolsMenuView.root(
                VIEWER_ID,
                List.of(StaffToolDefinition.STAFF_TOOLS)
        ));
        assertThrows(IllegalArgumentException.class, () -> StaffToolsMenuView.root(
                VIEWER_ID,
                List.of(StaffToolDefinition.REPORTS, StaffToolDefinition.REPORTS)
        ));
    }

    @Test
    void targetPickerExcludesTheViewerAndSortsImmutableTargetSnapshots() {
        UUID alphaId = UUID.fromString("66000000-0000-0000-0000-000000000010");
        UUID betaId = UUID.fromString("66000000-0000-0000-0000-000000000011");
        StaffToolsMenuView.TargetPicker picker = StaffToolsMenuView.TargetPicker.fromCandidates(
                VIEWER_ID,
                StaffToolDefinition.PLAYER_INSPECTOR,
                List.of(
                        new StaffToolsMenuView.TargetEntry(betaId, "Beta"),
                        new StaffToolsMenuView.TargetEntry(VIEWER_ID, "Viewer"),
                        new StaffToolsMenuView.TargetEntry(alphaId, "alpha")
                ),
                0
        );

        assertEquals(List.of("alpha", "Beta"), picker.targets().stream()
                .map(StaffToolsMenuView.TargetEntry::playerName)
                .toList());
        assertEquals(alphaId, picker.targetAtPageIndex(0).playerId());
        assertEquals(betaId, picker.targetAtPageIndex(1).playerId());
        assertNull(picker.targetAtPageIndex(2));
        assertFalse(picker.truncated());
        assertEquals(1, picker.totalPages());
    }

    @Test
    void targetPickerBoundsLargeListsAndClampsToTheLastPage() {
        List<StaffToolsMenuView.TargetEntry> candidates = IntStream.range(0, StaffToolsMenuView.MAX_TARGETS + 10)
                .mapToObj(index -> new StaffToolsMenuView.TargetEntry(
                        new UUID(0L, index + 1L),
                        "Player%03d".formatted(index)
                ))
                .toList();

        StaffToolsMenuView.TargetPicker picker = StaffToolsMenuView.TargetPicker.fromCandidates(
                VIEWER_ID,
                StaffToolDefinition.FREEZE,
                candidates,
                99
        );

        assertEquals(StaffToolsMenuView.MAX_TARGETS, picker.targets().size());
        assertTrue(picker.truncated());
        assertEquals(4, picker.totalPages());
        assertEquals(3, picker.page());
        assertTrue(picker.hasPreviousPage());
        assertFalse(picker.hasNextPage());
        assertEquals("Player135", picker.targetAtPageIndex(0).playerName());
    }

    @Test
    void targetPickerRejectsANonTargetToolAndDuplicateStoredTargets() {
        StaffToolsMenuView.TargetEntry target = new StaffToolsMenuView.TargetEntry(
                UUID.fromString("66000000-0000-0000-0000-000000000020"),
                "Target"
        );

        assertThrows(IllegalArgumentException.class, () -> new StaffToolsMenuView.TargetPicker(
                VIEWER_ID,
                StaffToolDefinition.REPORTS,
                List.of(target),
                0,
                false
        ));
        assertThrows(IllegalArgumentException.class, () -> new StaffToolsMenuView.TargetPicker(
                VIEWER_ID,
                StaffToolDefinition.SPECTATE,
                List.of(target, target),
                0,
                false
        ));
    }

    @Test
    void fixedSlotsNeverRouteLowerInventoryClicksAsStaffMenuActions() {
        assertEquals(0, StaffToolsMenuRenderer.rootToolIndex(28));
        assertEquals(7, StaffToolsMenuRenderer.rootToolIndex(32));
        assertEquals(-1, StaffToolsMenuRenderer.rootToolIndex(36));
        assertEquals(0, StaffToolsMenuRenderer.targetContentIndex(0));
        assertEquals(44, StaffToolsMenuRenderer.targetContentIndex(44));
        assertEquals(-1, StaffToolsMenuRenderer.targetContentIndex(45));
    }

    @Test
    void filteredRootKeepsToolPositionsAndAllowsAnEmptySessionDashboard() {
        StaffToolsMenuView.Root root = StaffToolsMenuView.root(VIEWER_ID, List.of(StaffToolDefinition.REPORTS));
        assertEquals(StaffToolDefinition.REPORTS, root.toolAt(StaffToolsMenuRenderer.rootToolIndex(14)));
        assertNull(root.toolAt(StaffToolsMenuRenderer.rootToolIndex(10)));
        assertTrue(StaffToolsMenuView.root(VIEWER_ID, List.of()).tools().isEmpty());
        for (int slot : List.of(StaffToolsMenuRenderer.INFO_SLOT, StaffToolsMenuRenderer.CLOSE_SLOT,
                StaffToolsMenuRenderer.EXIT_SLOT, StaffToolsMenuRenderer.CONFIRM_EXIT_SLOT,
                StaffToolsMenuRenderer.CANCEL_EXIT_SLOT)) {
            assertEquals(-1, StaffToolsMenuRenderer.rootToolIndex(slot));
        }
        assertTrue(StaffToolsMenuRenderer.EXIT_SLOT != StaffToolsMenuRenderer.CLOSE_SLOT);
        assertTrue(StaffToolsMenuRenderer.CONFIRM_EXIT_SLOT != StaffToolsMenuRenderer.CLOSE_SLOT);
    }

    @Test
    void investigationRoutesOnlyAuthorizedActionsAndKeepsStableSlots() {
        var target = new StaffToolsMenuView.TargetEntry(UUID.randomUUID(), "Player");
        var actions = InvestigationMenuAction.available("enthusiastaff.history.view"::equals);
        var view = new StaffToolsMenuView.Investigation(VIEWER_ID, target, actions);
        assertEquals(List.of(InvestigationMenuAction.HISTORY), actions);
        assertEquals(InvestigationMenuAction.HISTORY, view.actionAt(12));
        assertNull(view.actionAt(10));
        assertNull(view.actionAt(53));
        assertNull(view.actionAt(-1));
        assertNull(view.actionAt(54));
        assertEquals("history Player", view.actionAt(12).command(target.playerName()));
        assertTrue(InvestigationMenuAction.available(ignored -> false).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> view.actions().clear());
        assertThrows(IllegalArgumentException.class, () -> new StaffToolsMenuView.Investigation(
                VIEWER_ID, new StaffToolsMenuView.TargetEntry(VIEWER_ID, "Self"), actions));
    }

    @Test
    void investigationActionSlotsAreDistinctAndNeverUseNavigationSlots() {
        var actions = InvestigationMenuAction.available(ignored -> true);
        assertEquals(actions.size(), actions.stream().map(InvestigationMenuAction::slot).distinct().count());
        for (int slot : List.of(45, 46, 48, 49, 51, 52, 53, 54)) {
            assertNull(InvestigationMenuAction.atSlot(slot));
        }
        assertEquals("client Player", InvestigationMenuAction.CLIENT.command("Player"));
        assertEquals("punish Player", InvestigationMenuAction.PUNISHMENT.command("Player"));
    }
}

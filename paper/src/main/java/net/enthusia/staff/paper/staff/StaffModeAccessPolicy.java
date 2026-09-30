package net.enthusia.staff.paper.staff;

import java.util.Objects;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.bukkit.event.inventory.ClickType;

final class StaffModeAccessPolicy {
    private StaffModeAccessPolicy() {
    }

    static boolean blocksAllInventoryMutation(StaffRank rank) {
        return rank == null || rank == StaffRank.HELPER || rank == StaffRank.SYSTEM;
    }

    static boolean blocksEnderChestOpen(StaffRank rank) {
        return rank != StaffRank.ADMIN && rank != StaffRank.FOUNDER;
    }

    static boolean blocksEnderChestMutation(StaffRank rank) {
        return rank != StaffRank.FOUNDER;
    }

    static boolean blocksInventoryMutation(StaffRank rank, boolean enderChestView) {
        return blocksAllInventoryMutation(rank)
                || (enderChestView && blocksEnderChestMutation(rank));
    }

    static boolean blocksStaffToolTransfer(
            ClickType click,
            boolean currentItemIsStaffTool,
            boolean cursorIsStaffTool,
            boolean referencedHotbarItemIsStaffTool,
            boolean offhandItemIsStaffTool
    ) {
        Objects.requireNonNull(click, "click");
        if (currentItemIsStaffTool || cursorIsStaffTool) {
            return true;
        }
        return switch (click) {
            case NUMBER_KEY -> referencedHotbarItemIsStaffTool;
            case SWAP_OFFHAND -> offhandItemIsStaffTool;
            default -> false;
        };
    }

    static boolean usesCreativeMode(StaffRank rank) {
        return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
    }

    /** Default game mode applied on Staff Mode entry/profile refresh. */
    static GameMode requiredGameMode(StaffRank rank) {
        return usesCreativeMode(rank) ? GameMode.CREATIVE : GameMode.SPECTATOR;
    }

    /** Runtime modes a rank may deliberately switch to while Staff Mode remains active. */
    static boolean allowsGameMode(StaffRank rank, GameMode gameMode) {
        Objects.requireNonNull(gameMode, "gameMode");
        if (rank == StaffRank.HELPER) {
            return gameMode == GameMode.SURVIVAL || gameMode == GameMode.SPECTATOR;
        }
        if (rank == null || rank == StaffRank.SYSTEM) {
            return false;
        }
        return gameMode == requiredGameMode(rank);
    }

    static boolean hasAdvancedStaffTools(StaffRank rank) {
        return rank != null && rank != StaffRank.HELPER && rank != StaffRank.SYSTEM;
    }
}

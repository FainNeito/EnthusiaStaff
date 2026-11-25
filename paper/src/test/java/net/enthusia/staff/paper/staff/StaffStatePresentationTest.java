package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class StaffStatePresentationTest {
    @Test
    void frontierUsesItsChatPaletteAndDistinguishesVanishStates() {
        Component heading = Component.text("[Staff Mode]", TextColor.color(0xE06B52))
                .append(Component.text(" » ", TextColor.color(0xB5ACA0)));
        assertEquals(heading.append(Component.text("Vanish ", TextColor.color(0xEFE5D0))
                        .append(Component.text("ON", TextColor.color(0xD6B56C), TextDecoration.BOLD))),
                StaffStatePresentation.frontierIndicator(true, true));
        assertEquals(heading.append(Component.text("Vanish ", TextColor.color(0xEFE5D0))
                        .append(Component.text("OFF", TextColor.color(0xE06B52), TextDecoration.BOLD))),
                StaffStatePresentation.frontierIndicator(true, false));
    }

    @Test
    void staffModeAlwaysShowsExplicitVanishOnState() {
        assertEquals(
                Component.text("STAFF MODE", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("VANISH ", NamedTextColor.GRAY))
                        .append(Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)),
                StaffStatePresentation.indicator(true, true)
        );
    }

    @Test
    void staffModeAlwaysShowsExplicitVanishOffState() {
        assertEquals(
                Component.text("STAFF MODE", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("VANISH ", NamedTextColor.GRAY))
                        .append(Component.text("OFF", NamedTextColor.GOLD, TextDecoration.BOLD)),
                StaffStatePresentation.indicator(true, false)
        );
    }

    @Test
    void vanishOutsideStaffModeStillHasPersistentIndicator() {
        assertEquals(
                Component.text("VANISH", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)),
                StaffStatePresentation.indicator(false, true)
        );
    }
}

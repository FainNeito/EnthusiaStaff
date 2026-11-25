package net.enthusia.staff.paper.presentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Colors shared by Frontier staff feedback and the RoseChat staff channels. */
public final class FrontierStaffPresentation {
    public static final TextColor ACCENT = TextColor.color(0xE06B52);
    public static final TextColor PARCHMENT = TextColor.color(0xEFE5D0);
    public static final TextColor MUTED = TextColor.color(0xB5ACA0);
    public static final TextColor GOLD = TextColor.color(0xD6B56C);

    private FrontierStaffPresentation() {}

    public static boolean appliesTo(String serverId) {
        return "FRONTIER".equalsIgnoreCase(serverId);
    }

    public static Component notice(String label, Component message) {
        return Component.text("[" + label + "]", ACCENT)
                .append(Component.text(" » ", MUTED))
                .append(message.colorIfAbsent(PARCHMENT));
    }
}

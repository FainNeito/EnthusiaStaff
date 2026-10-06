package net.enthusia.staff.paper.punishment;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/** Player-facing groups; policy families and reason IDs remain unchanged. */
enum PunishmentGuiCategory {
    CHAT("chat", "Chat & Spam", "Spam, language, and public topics", Material.WRITABLE_BOOK,
            NamedTextColor.AQUA, Set.of("chat", "spam", "language", "politics")),
    HARASSMENT("harassment", "Harassment & Hate", "Targeted abuse, slurs, and harassment", Material.REDSTONE,
            NamedTextColor.RED, Set.of("hate", "harassment")),
    SAFETY("safety", "Safety & Privacy", "Threats, exploitation, and personal information", Material.SHIELD,
            NamedTextColor.RED, Set.of("safety", "privacy")),
    CONTENT("content", "Identity & Content", "Profiles, impersonation, and inappropriate content", Material.PAINTING,
            NamedTextColor.LIGHT_PURPLE, Set.of("content", "identity")),
    ADVERTISING("advertising", "Advertising & Scams", "Server ads, promotions, and deceptive links", Material.EMERALD,
            NamedTextColor.GREEN, Set.of("advertising")),
    CHEATING("cheating", "Cheating", "Unfair clients, X-ray, and automation", Material.DIAMOND_SWORD,
            NamedTextColor.GOLD, Set.of("cheating")),
    EXPLOITS("exploits", "Exploits & Disruption", "Duplication, exploit abuse, and server impact", Material.TNT,
            NamedTextColor.GOLD, Set.of("exploit", "mechanics", "complicity")),
    ACCOUNTS("accounts", "Accounts & Evasion", "Account misuse and evading punishments", Material.ENDER_EYE,
            NamedTextColor.LIGHT_PURPLE, Set.of("account", "evasion")),
    REPORTS("reports", "Reports & Cooperation", "False reports, evidence, and staff instructions", Material.LECTERN,
            NamedTextColor.AQUA, Set.of("reports", "staff", "dishonesty")),
    TRADE("trade", "Trade & Reputation", "Market rules and reputation misuse", Material.GOLD_INGOT,
            NamedTextColor.GREEN, Set.of("market", "reputation")),
    OTHER("other", "Other Rules", "Additional configured reasons", Material.MAP,
            NamedTextColor.GRAY, Set.of());

    private final String id;
    private final String title;
    private final String description;
    private final Material material;
    private final NamedTextColor color;
    private final Set<String> families;

    PunishmentGuiCategory(String id, String title, String description, Material material,
            NamedTextColor color, Set<String> families) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.material = material;
        this.color = color;
        this.families = families;
    }

    String id() { return id; }
    String title() { return title; }
    String description() { return description; }
    Material material() { return material; }
    NamedTextColor color() { return color; }
    Set<String> families() { return families; }

    boolean includes(String family) {
        return this == OTHER ? Arrays.stream(values()).filter(value -> value != OTHER)
                .noneMatch(value -> value.families.contains(family)) : families.contains(family);
    }

    static PunishmentGuiCategory forFamily(String family) {
        return Arrays.stream(values()).filter(value -> value != OTHER && value.families.contains(family))
                .findFirst().orElse(OTHER);
    }

    static PunishmentGuiCategory byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }

    static List<PunishmentGuiCategory> ordered() {
        return List.of(values());
    }
}

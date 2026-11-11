package net.enthusia.staff.paper.punishment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class PunishmentReasonPresentation {
    private PunishmentReasonPresentation() {
    }

    static String name(Optional<ReasonPolicyRepository.ReasonDescriptor> descriptor) {
        return descriptor.map(ReasonPolicyRepository.ReasonDescriptor::publicReason)
                .orElse("Unknown reason");
    }

    static List<Component> lore(
            String reasonId,
            Optional<ReasonPolicyRepository.ReasonDescriptor> descriptor
    ) {
        List<Component> lore = new ArrayList<>();
        if (descriptor.isEmpty()) {
            lore.add(Component.text("Reason ID: " + reasonId, NamedTextColor.DARK_GRAY));
            addUnknown(lore);
        } else {
            addKnown(lore, descriptor.orElseThrow());
        }
        return List.copyOf(lore);
    }

    private static void addUnknown(List<Component> lore) {
        lore.add(Component.text("This reason is no longer configured.", NamedTextColor.RED));
        lore.add(Component.text("Go back and choose a current reason.", NamedTextColor.YELLOW));
    }

    private static void addKnown(
            List<Component> lore,
            ReasonPolicyRepository.ReasonDescriptor descriptor
    ) {
        switch (descriptor.availability()) {
            case ACTIVE -> { }
            case ALIAS -> addAlias(lore);
            case REMOVED -> addRemoved(lore);
            default -> throw new IllegalStateException(
                    "Unsupported reason availability: " + descriptor.availability()
            );
        }
    }

    private static void addAlias(List<Component> lore) {
        lore.add(Component.text("This saved reason was renamed.", NamedTextColor.AQUA));
        lore.add(Component.text("The current reason will be used.", NamedTextColor.GRAY));
    }

    private static void addRemoved(List<Component> lore) {
        lore.add(Component.text("This reason is no longer available.", NamedTextColor.RED));
        lore.add(Component.text("It remains visible in past cases.", NamedTextColor.GRAY));
    }
}

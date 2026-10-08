package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.paper.config.MessageCatalog;
import net.enthusia.staff.paper.config.MessageKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class VanishCommandMessagesTest {
    private static final String VANISH_LABEL = "vanish";
    @Test
    void deniedPermissionUsesShippedWordingWithoutTouchingVanish() {
        List<Component> messages = new ArrayList<>();
        VanishCommand command = new VanishCommand(() -> OperationalMode.ACTIVE, null);

        assertTrue(command.onCommand(sender(false, messages), null, VANISH_LABEL, new String[0]));

        assertEquals(
                List.of("You do not have permission to change vanish or spectator tab visibility."),
                messages.stream().map(VanishCommandMessagesTest::text).toList()
        );
    }

    @Test
    void nonPlayerUsesShippedWordingWithoutTouchingVanish() {
        List<Component> messages = new ArrayList<>();
        VanishCommand command = new VanishCommand(() -> OperationalMode.ACTIVE, null);

        assertTrue(command.onCommand(sender(true, messages), null, VANISH_LABEL, new String[0]));

        assertEquals(
                List.of("Only a player can change vanish or spectator tab visibility."),
                messages.stream().map(VanishCommandMessagesTest::text).toList()
        );
    }

    @Test
    void invalidArgumentsUseEscapedAliasAndShippedUsage() {
        List<Component> messages = new ArrayList<>();
        VanishCommand command = new VanishCommand(() -> OperationalMode.ACTIVE, null);

        assertTrue(command.onCommand(player(messages), null, "v", new String[]{"extra"}));

        assertEquals(
                List.of("Usage: /v | /v tab <show|hide>"),
                messages.stream().map(VanishCommandMessagesTest::text).toList()
        );
    }

    @Test
    void configuredTextIsReadFromTheLatestCatalog() {
        List<Component> messages = new ArrayList<>();
        EnumMap<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
        templates.putAll(MessageCatalog.builtIn().templates());
        templates.put(MessageKey.VANISH_PERMISSION_DENIED, "Custom vanish denial.");
        MessageCatalog custom = new MessageCatalog(templates);

        VanishCommand command = new VanishCommand(() -> OperationalMode.ACTIVE, null);
        command.configureMessages(() -> custom);

        assertTrue(command.onCommand(sender(false, messages), null, VANISH_LABEL, new String[0]));
        assertEquals(List.of("Custom vanish denial."), messages.stream().map(VanishCommandMessagesTest::text).toList());
    }

    @Test
    void currentModePlaceholderPreservesExpectedWording() {
        Component rendered = MessageCatalog.builtIn().component(
                MessageKey.VANISH_MODE_DISABLED,
                java.util.Map.of("mode", OperationalMode.READ_ONLY_FAILURE)
        );

        assertEquals("Vanish enable is disabled while moderation is READ_ONLY_FAILURE.", text(rendered));
    }

    @Test
    void commandReadsNewCatalogWithoutBeingReconstructed() {
        List<Component> messages = new ArrayList<>();
        EnumMap<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
        templates.putAll(MessageCatalog.builtIn().templates());
        templates.put(MessageKey.VANISH_PERMISSION_DENIED, "Updated denial.");
        java.util.concurrent.atomic.AtomicReference<MessageCatalog> live = new java.util.concurrent.atomic.AtomicReference<>(
                MessageCatalog.builtIn()
        );

        VanishCommand command = new VanishCommand(() -> OperationalMode.ACTIVE, null);
        command.configureMessages(live::get);
        assertTrue(command.onCommand(sender(false, messages), null, VANISH_LABEL, new String[0]));

        live.set(new MessageCatalog(templates));
        assertTrue(command.onCommand(sender(false, messages), null, VANISH_LABEL, new String[0]));

        assertEquals(
                List.of(
                        "You do not have permission to change vanish or spectator tab visibility.",
                        "Updated denial."
                ),
                messages.stream().map(VanishCommandMessagesTest::text).toList()
        );
    }

    private static CommandSender sender(boolean allowed, List<Component> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> allowed;
                    case "sendMessage" -> {
                        messages.add((Component) args[0]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Player player(List<Component> messages) {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> true;
                    case "sendMessage" -> {
                        messages.add((Component) args[0]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    private static String text(Component component) {
        StringBuilder builder = new StringBuilder();
        if (component instanceof TextComponent text) {
            builder.append(text.content());
        }
        component.children().forEach(child -> builder.append(text(child)));
        return builder.toString();
    }
}

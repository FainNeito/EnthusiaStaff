package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.miniplaceholders.api.Expansion;
import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MiniPlaceholdersPublicOnlineBridgeTest {
    @Test
    void registersTheDocumentedGlobalSyntaxAndCleansItUp() {
        Runnable cleanup = MiniPlaceholdersPublicOnlineBridge.register(
                () -> 7,
                LoggerFactory.getLogger(MiniPlaceholdersPublicOnlineBridgeTest.class)
        );
        try {
            Expansion expansion = MiniPlaceholders.expansionByName("enthusiastaff");
            assertNotNull(expansion);
            assertTrue(expansion.hasGlobalPlaceholder("public_online"));
            Component rendered = MiniMessage.miniMessage().deserialize(
                    PublicOnlineCountPolicy.PLACEHOLDER,
                    MiniPlaceholders.globalPlaceholders()
            );
            assertEquals(Component.text("7"), rendered);
        } finally {
            cleanup.run();
        }
        assertNull(MiniPlaceholders.expansionByName("enthusiastaff"));
    }
}

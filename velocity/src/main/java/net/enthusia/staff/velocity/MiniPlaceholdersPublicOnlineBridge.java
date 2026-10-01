package net.enthusia.staff.velocity;

import io.github.miniplaceholders.api.Expansion;
import io.github.miniplaceholders.api.MiniPlaceholders;
import java.util.Objects;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.slf4j.Logger;

/** Registers the Staff-owned public network count through MiniPlaceholders. */
final class MiniPlaceholdersPublicOnlineBridge {
    private static final String EXPANSION = "enthusiastaff";
    private static final String KEY = "public_online";

    private MiniPlaceholdersPublicOnlineBridge() {
    }

    static Runnable register(IntSupplier count, Logger logger) {
        Objects.requireNonNull(count, "count");
        Objects.requireNonNull(logger, "logger");
        if (MiniPlaceholders.expansionByName(EXPANSION) != null) {
            throw new IllegalStateException("MiniPlaceholders expansion 'enthusiastaff' is already registered");
        }
        Expansion expansion = Expansion.builder(EXPANSION)
                .globalPlaceholder(KEY, (queue, context) -> Tag.selfClosingInserting(
                        Component.text(Integer.toString(count.getAsInt()))
                ))
                .build();
        expansion.register();
        if (logger.isInfoEnabled()) {
            logger.info("Registered Staff public-online placeholder {}", PublicOnlineCountPolicy.PLACEHOLDER);
        }
        return () -> {
            if (expansion.registered()) {
                expansion.unregister();
            }
        };
    }
}

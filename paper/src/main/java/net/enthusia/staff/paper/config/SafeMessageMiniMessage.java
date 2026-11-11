package net.enthusia.staff.paper.config;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

final class SafeMessageMiniMessage {
    private static final Pattern TAG = Pattern.compile("(?<!\\\\)<(/?)([^<>]+)>");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-f]{6}");
    private static final Set<String> SAFE_NAMES = Set.of(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white",
            "bold",
            "b",
            "italic",
            "em",
            "i",
            "underlined",
            "u",
            "strikethrough",
            "st",
            "obfuscated",
            "obf",
            "reset"
    );
    private static final TagResolver SAFE_TAGS = TagResolver.resolver(
            StandardTags.color(),
            StandardTags.decorations(),
            StandardTags.reset()
    );
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(SAFE_TAGS)
            .build();

    private SafeMessageMiniMessage() {
    }

    static void validate(String template) {
        Matcher matcher = TAG.matcher(template);
        while (matcher.find()) {
            String tag = matcher.group(2).trim().toLowerCase(Locale.ROOT);
            if (!SAFE_NAMES.contains(tag) && !HEX_COLOR.matcher(tag).matches()) {
                throw new IllegalArgumentException(
                        "message contains unsupported MiniMessage tag <" + matcher.group(2) + ">"
                );
            }
        }
        try {
            MINI_MESSAGE.deserialize(template);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("message contains invalid MiniMessage markup", exception);
        }
    }

    static Component deserialize(String template) {
        return MINI_MESSAGE.deserialize(template);
    }

    static String escapePlaceholder(Object value) {
        String literal = String.valueOf(value);
        return literal.replace("\\", "\\\\").replace("<", "\\<");
    }
}

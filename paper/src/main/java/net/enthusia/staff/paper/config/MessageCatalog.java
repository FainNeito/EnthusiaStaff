package net.enthusia.staff.paper.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

public record MessageCatalog(Map<MessageKey, String> templates) {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9-]*)}");

    public MessageCatalog {
        Objects.requireNonNull(templates, "templates");
        EnumMap<MessageKey, String> copy = new EnumMap<>(MessageKey.class);
        for (MessageKey key : MessageKey.values()) {
            String template = templates.get(key);
            if (template == null || template.isBlank()) {
                throw new IllegalArgumentException("message " + key.path() + " must be present and non-blank");
            }
            validateTemplate(key, template);
            copy.put(key, template);
        }
        if (templates.size() != copy.size()) {
            throw new IllegalArgumentException("message catalog contains unsupported keys");
        }
        templates = Map.copyOf(copy);
    }

    public static MessageCatalog builtIn() {
        EnumMap<MessageKey, String> defaults = new EnumMap<>(MessageKey.class);
        for (MessageKey key : MessageKey.values()) {
            defaults.put(key, key.defaultText());
        }
        return new MessageCatalog(defaults);
    }

    public String text(MessageKey key) {
        Objects.requireNonNull(key, "key");
        requireNoPlaceholders(key);
        return templates.get(key);
    }

    public Component component(MessageKey key) {
        return SafeMessageMiniMessage.deserialize(text(key));
    }

    public String render(MessageKey key, Map<String, ?> values) {
        return interpolate(key, values, String::valueOf);
    }

    public Component component(MessageKey key, Map<String, ?> values) {
        return SafeMessageMiniMessage.deserialize(interpolate(
                key,
                values,
                SafeMessageMiniMessage::escapePlaceholder
        ));
    }

    private String interpolate(
            MessageKey key,
            Map<String, ?> values,
            Function<Object, String> valueFormatter
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(valueFormatter, "valueFormatter");
        if (!values.keySet().equals(key.placeholders())) {
            throw new IllegalArgumentException(
                    "message " + key.path() + " requires placeholders " + key.placeholders()
                            + " but received " + values.keySet()
            );
        }
        String template = templates.get(key);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            Object value = Objects.requireNonNull(values.get(matcher.group(1)), matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(valueFormatter.apply(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static void requireNoPlaceholders(MessageKey key) {
        if (!key.placeholders().isEmpty()) {
            throw new IllegalArgumentException(
                    "message " + key.path() + " requires placeholders " + key.placeholders()
            );
        }
    }

    static void validateTemplate(MessageKey key, String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        java.util.HashSet<String> found = new java.util.HashSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        Set<String> expected = key.placeholders();
        if (!found.equals(expected)) {
            throw new IllegalArgumentException(
                    "message " + key.path() + " must use placeholders " + expected + " but uses " + found
            );
        }
        String stripped = PLACEHOLDER.matcher(template).replaceAll("");
        if (stripped.indexOf('{') >= 0 || stripped.indexOf('}') >= 0) {
            throw new IllegalArgumentException("message " + key.path() + " contains an invalid placeholder token");
        }
        SafeMessageMiniMessage.validate(template);
    }
}

package net.enthusia.staff.paper.config;

import java.util.List;
import java.util.Objects;

public record ConfigurationValidationReport(
        List<Entry> entries,
        List<String> errors
) {
    public ConfigurationValidationReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public record Entry(String source, String version) {
        public Entry {
            source = requireText(source, "source");
            version = requireText(version, "version");
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}

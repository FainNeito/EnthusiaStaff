package net.enthusia.staff.paper.config;

import java.util.Objects;

public record MessageConfigurationSnapshot(
        int schemaVersion,
        MessageCatalog catalog
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public MessageConfigurationSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported messages schema version " + schemaVersion);
        }
        Objects.requireNonNull(catalog, "catalog");
    }
}

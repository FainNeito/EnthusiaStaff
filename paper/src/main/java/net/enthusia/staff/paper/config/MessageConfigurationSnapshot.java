package net.enthusia.staff.paper.config;

import java.util.Objects;

public record MessageConfigurationSnapshot(
        int schemaVersion,
        MessageCatalog catalog
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int EARLIEST_SUPPORTED_SCHEMA_VERSION = 1;

    public MessageConfigurationSnapshot {
        if (schemaVersion < EARLIEST_SUPPORTED_SCHEMA_VERSION
                || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported messages schema version " + schemaVersion);
        }
        Objects.requireNonNull(catalog, "catalog");
    }
}

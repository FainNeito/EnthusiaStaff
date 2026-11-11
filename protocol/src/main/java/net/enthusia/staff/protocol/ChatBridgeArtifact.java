package net.enthusia.staff.protocol;

import java.util.Locale;
import java.util.Objects;

/** One bounded binary rich-chat artifact carried independently from styled text metadata. */
public record ChatBridgeArtifact(
        Kind kind,
        int position,
        String filename,
        String contentType,
        String altText,
        byte[] data
) {
    public static final int MAX_ARTIFACT_BYTES = 262_144;

    public enum Kind {
        ITEM,
        INVENTORY,
        ENDER_CHEST,
        MAP,
        TOOLTIP,
        OTHER
    }

    public ChatBridgeArtifact {
        kind = Objects.requireNonNull(kind, "kind");
        if (position < 0 || position > 2_000) {
            throw new IllegalArgumentException("artifact position is invalid");
        }
        filename = safeFilename(filename);
        contentType = requireText(contentType, "contentType", 64).toLowerCase(Locale.ROOT);
        if (!"image/png".equals(contentType)) {
            throw new IllegalArgumentException("rich chat artifacts must currently be PNG images");
        }
        altText = requireText(altText, "altText", 256);
        data = Objects.requireNonNull(data, "data").clone();
        if (data.length < 1 || data.length > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("rich chat artifact exceeds maximum size");
        }
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    private static String safeFilename(String value) {
        String filename = requireText(value, "filename", 96);
        if (!filename.endsWith(".png") || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) {
            throw new IllegalArgumentException("artifact filename is invalid");
        }
        return filename;
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " contains control characters");
        }
        return value;
    }
}

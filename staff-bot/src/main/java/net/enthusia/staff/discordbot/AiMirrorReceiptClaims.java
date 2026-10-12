package net.enthusia.staff.discordbot;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process fencing for fast registration and slow journal recovery.
 *
 * Both HTTP paths must claim the opaque receipt before attempting HTTP or
 * changing its persisted revision. The encrypted journal independently
 * authenticates the receipt, and remains authoritative across restarts.
 * No original text, external IDs, or bearer credentials are retained here.
 */
final class AiMirrorReceiptClaims {
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    boolean tryClaim(AiMirrorEncryptedOutbox.Saved saved) {
        return active.add(key(saved));
    }

    void release(AiMirrorEncryptedOutbox.Saved saved) {
        if (!active.remove(key(saved))) {
            throw new IllegalStateException("unclaimed mirror receipt");
        }
    }

    int activeCount() {
        return active.size();
    }

    private static String key(AiMirrorEncryptedOutbox.Saved saved) {
        return Objects.requireNonNull(
                Objects.requireNonNull(saved, "receipt").fileName(), "receipt name");
    }
}

package net.enthusia.staff.protocol;

/** Short-lived StaffBot Discord publishing readiness signal for authoritative chat cutover. */
public record ChatBridgeHealthMessage(
        boolean ready,
        long createdAtEpochMillis,
        long expiresAtEpochMillis
) {
    public static final long MAX_LIFETIME_MILLIS = 30_000L;

    public ChatBridgeHealthMessage {
        if (createdAtEpochMillis <= 0L
                || expiresAtEpochMillis <= createdAtEpochMillis
                || expiresAtEpochMillis - createdAtEpochMillis > MAX_LIFETIME_MILLIS) {
            throw new IllegalArgumentException("chat bridge health lifetime is invalid");
        }
    }

    public boolean isExpired(long nowEpochMillis) {
        return nowEpochMillis > expiresAtEpochMillis;
    }
}

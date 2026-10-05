package net.enthusia.staff.domain.staff;

/**
 * Separates network-wide Staff Mode intent from ownership of a backend-local player-state snapshot.
 *
 * <p>An ACTIVE session whose server id is {@link #DETACHED_SERVER_ID} keeps Staff Mode active
 * network-wide while no backend owns/applies its saved-state profile. A joining backend must
 * capture its own native player state and atomically rebind the session before applying Staff Mode.
 */
public final class StaffSessionOwnership {
    public static final String DETACHED_SERVER_ID = "__network_detached__";

    private StaffSessionOwnership() {
    }

    public static boolean detached(String serverId) {
        return DETACHED_SERVER_ID.equals(serverId);
    }
}

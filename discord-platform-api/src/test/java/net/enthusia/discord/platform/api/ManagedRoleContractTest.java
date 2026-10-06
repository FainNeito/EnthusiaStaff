package net.enthusia.discord.platform.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManagedRoleContractTest {
    private static final String LUMA_GUILDS = "luma-guilds";

    @Test
    void acceptsStableNamespaceAndRoleKey() {
        ManagedRoleNamespace namespace = new ManagedRoleNamespace(LUMA_GUILDS);
        ManagedRoleKey key = new ManagedRoleKey(namespace, "guild:123e4567-e89b-12d3-a456-426614174000");

        assertEquals(LUMA_GUILDS, key.namespace().value());
        assertTrue(key.localKey().startsWith("guild:"));
    }

    @Test
    void rejectsUnsafeNamespaceAndRoleKey() {
        assertThrows(IllegalArgumentException.class, () -> new ManagedRoleNamespace("Luma Guilds"));
        ManagedRoleNamespace namespace = new ManagedRoleNamespace(LUMA_GUILDS);
        assertThrows(IllegalArgumentException.class, () -> new ManagedRoleKey(namespace, "bad key"));
    }

    @Test
    void claimDefensivelyCopiesDesiredAccounts() {
        UUID first = UUID.randomUUID();
        Set<UUID> source = new HashSet<>();
        source.add(first);
        ManagedRoleClaim claim = new ManagedRoleClaim(
                new ManagedRoleKey(new ManagedRoleNamespace("playtime-numerals"), "tier:xii"),
                "Playtime XII",
                source);

        source.clear();

        assertTrue(source.isEmpty());
        assertEquals(Set.of(first), claim.desiredMinecraftAccounts());
    }

    @Test
    void claimAcceptsOptionalExistingDiscordRoleIdAndRejectsUnsafeValues() {
        ManagedRoleKey key = new ManagedRoleKey(
                new ManagedRoleNamespace("playtime-numerals"),
                "tier:xii");
        ManagedRoleClaim claim = new ManagedRoleClaim(
                key,
                "Playtime XII",
                Optional.of("1552390213500928122"),
                Set.of());

        assertEquals(Optional.of("1552390213500928122"), claim.existingDiscordRoleId());
        assertThrows(IllegalArgumentException.class, () -> new ManagedRoleClaim(
                key, "Playtime XII", Optional.of("not-a-role"), Set.of()));
    }

    @Test
    void claimRejectsControlCharactersAndOversizedMembership() {
        ManagedRoleKey key = new ManagedRoleKey(new ManagedRoleNamespace(LUMA_GUILDS), "guild:one");
        assertThrows(IllegalArgumentException.class, () -> new ManagedRoleClaim(key, "Guild\nRole", Set.of()));

        Set<UUID> oversized = new HashSet<>();
        for (int index = 0; index <= ManagedRoleClaim.MAX_DESIRED_ACCOUNTS; index++) {
            oversized.add(UUID.randomUUID());
        }
        assertThrows(IllegalArgumentException.class, () -> new ManagedRoleClaim(key, "Guild Role", oversized));
    }

    @Test
    void resultAllowsAggregatesWithoutDiscordIdentityDisclosure() {
        ManagedRoleReconcileResult result = new ManagedRoleReconcileResult(
                ManagedRoleReconcileStatus.APPLIED,
                3,
                2,
                1,
                0);

        assertEquals(2, result.resolvedDiscordAccounts());
        assertEquals(1, result.rolesAdded());
    }

    @Test
    void resultRejectsImpossibleCounts() {
        assertThrows(IllegalArgumentException.class, () -> new ManagedRoleReconcileResult(
                ManagedRoleReconcileStatus.APPLIED,
                1,
                2,
                0,
                0));
        assertThrows(IllegalArgumentException.class, () -> new ManagedRoleReconcileResult(
                ManagedRoleReconcileStatus.APPLIED,
                1,
                1,
                -1,
                0));
    }
}

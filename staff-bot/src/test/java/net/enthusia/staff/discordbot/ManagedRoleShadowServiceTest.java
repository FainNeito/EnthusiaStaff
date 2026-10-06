package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import org.junit.jupiter.api.Test;

final class ManagedRoleShadowServiceTest {
    private static final String VALID_JSON = """
            {
              "version": 1,
              "namespace": "playtime-numerals",
              "localKey": "tier:xii",
              "displayName": "Playtime XII",
              "existingDiscordRoleId": "1552390213500928122",
              "desiredMinecraftAccounts": [],
              "delete": false
            }
            """;

    @Test
    void malformedClaimDoesNotAbortValidShadowClaims() {
        ReconciliationState valid = state("managed-role:valid", "resource-valid", VALID_JSON);
        ReconciliationState invalid = state("managed-role:invalid", "sensitive-resource-name", "{");

        ManagedRoleShadowService.Snapshot snapshot = ManagedRoleShadowService.decodeStates(
                List.of(valid, invalid),
                10,
                new ObjectMapper()
        );

        assertEquals(1, snapshot.claims().size());
        assertEquals(1, snapshot.invalidResourceRefs().size());
        assertFalse(snapshot.invalidResourceRefs().getFirst().contains("sensitive"));
        assertTrue(snapshot.incomplete());
        assertFalse(snapshot.truncated());
    }

    @Test
    void claimLimitMarksSnapshotIncompleteWithoutDecodingOverflowRow() {
        ReconciliationState valid = state("managed-role:valid", "resource-valid", VALID_JSON);
        ReconciliationState overflow = state("managed-role:overflow", "resource-overflow", "{");

        ManagedRoleShadowService.Snapshot snapshot = ManagedRoleShadowService.decodeStates(
                List.of(valid, overflow),
                1,
                new ObjectMapper()
        );

        assertEquals(1, snapshot.claims().size());
        assertEquals(0, snapshot.invalidResourceRefs().size());
        assertTrue(snapshot.truncated());
        assertTrue(snapshot.incomplete());
    }

    private static ReconciliationState state(String key, String resourceId, String desiredJson) {
        return new ReconciliationState(
                key,
                "MANAGED_ROLE",
                resourceId,
                desiredJson,
                Optional.empty(),
                "PENDING",
                0,
                Optional.empty(),
                Optional.empty(),
                0
        );
    }
}

package net.enthusia.staff.paper.discordplatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.enthusia.discord.platform.api.DiscordPlatformAvailability;
import net.enthusia.discord.platform.api.ManagedRoleClaim;
import net.enthusia.discord.platform.api.ManagedRoleDeleteResult;
import net.enthusia.discord.platform.api.ManagedRoleKey;
import net.enthusia.discord.platform.api.ManagedRoleNamespace;
import net.enthusia.discord.platform.api.ManagedRoleReconcileStatus;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class PaperManagedRolePlatformTest {
    private static final Instant NOW = Instant.parse("2026-10-05T22:00:00Z");
    private static final String LUMA_NAMESPACE = "luma-guilds";
    private final ExecutorService workers = Executors.newSingleThreadExecutor();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void closeWorkers() {
        workers.shutdownNow();
    }

    @Test
    void onlyExplicitMigrationNamespacesReceiveClients() {
        FakeStore store = new FakeStore();
        PaperManagedRolePlatform platform = platform(store);

        assertTrue(platform.clientFor(new ManagedRoleNamespace(LUMA_NAMESPACE)).isPresent());
        assertTrue(platform.clientFor(new ManagedRoleNamespace("playtime-numerals")).isPresent());
        assertTrue(platform.clientFor(new ManagedRoleNamespace("unknown-consumer")).isEmpty());
    }

    @Test
    void reconcilePublishesCompleteProviderNeutralDesiredState() throws Exception {
        FakeStore store = new FakeStore();
        PaperManagedRolePlatform platform = platform(store);
        ManagedRoleNamespace namespace = new ManagedRoleNamespace(LUMA_NAMESPACE);
        ManagedRoleKey key = new ManagedRoleKey(namespace, "guild:123e4567-e89b-12d3-a456-426614174000");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");

        var result = platform.clientFor(namespace).orElseThrow()
                .reconcile(new ManagedRoleClaim(
                        key,
                        "Example Guild",
                        Optional.of("1552390213500928122"),
                        Set.of(second, first)))
                .toCompletableFuture().join();

        assertEquals(ManagedRoleReconcileStatus.RETRY_SCHEDULED, result.status());
        assertEquals(2, result.desiredMinecraftAccounts());
        ReconciliationState persisted = store.only();
        assertEquals(PaperManagedRolePlatform.RESOURCE_TYPE, persisted.resourceType());
        assertEquals("PENDING", persisted.state());
        var desired = json.readTree(persisted.desiredStateJson());
        assertEquals(LUMA_NAMESPACE, desired.path("namespace").asText());
        assertEquals(key.localKey(), desired.path("localKey").asText());
        assertEquals("Example Guild", desired.path("displayName").asText());
        assertEquals("1552390213500928122", desired.path("existingDiscordRoleId").asText());
        assertFalse(desired.path("delete").asBoolean());
        assertEquals(first.toString(), desired.path("desiredMinecraftAccounts").get(0).asText());
        assertEquals(second.toString(), desired.path("desiredMinecraftAccounts").get(1).asText());
    }

    @Test
    void updatePreservesObservedStateAndUsesRevisionFence() {
        FakeStore store = new FakeStore();
        PaperManagedRolePlatform platform = platform(store);
        ManagedRoleNamespace namespace = new ManagedRoleNamespace("playtime-numerals");
        ManagedRoleKey key = new ManagedRoleKey(namespace, "tier:xii");

        platform.clientFor(namespace).orElseThrow()
                .reconcile(new ManagedRoleClaim(key, "Playtime XII", Set.of()))
                .toCompletableFuture().join();
        ReconciliationState first = store.only();
        store.rows.put(first.reconciliationKey(), new ReconciliationState(
                first.reconciliationKey(), first.resourceType(), first.resourceId(), first.desiredStateJson(),
                Optional.of("{\"roleId\":\"123\"}"), "SHADOW_MATCH", 0,
                Optional.empty(), Optional.empty(), first.revision()
        ));

        platform.clientFor(namespace).orElseThrow()
                .reconcile(new ManagedRoleClaim(key, "Playtime XII", Set.of(UUID.randomUUID())))
                .toCompletableFuture().join();

        ReconciliationState second = store.only();
        assertEquals(Optional.of("{\"roleId\":\"123\"}"), second.observedStateJson());
        assertEquals(1L, second.revision());
        assertEquals("PENDING", second.state());
    }

    @Test
    void deletePublishesNamespaceScopedTombstone() throws Exception {
        FakeStore store = new FakeStore();
        PaperManagedRolePlatform platform = platform(store);
        ManagedRoleNamespace namespace = new ManagedRoleNamespace(LUMA_NAMESPACE);
        ManagedRoleKey key = new ManagedRoleKey(namespace, "guild:one");

        ManagedRoleDeleteResult result = platform.clientFor(namespace).orElseThrow()
                .delete(key).toCompletableFuture().join();

        assertEquals(ManagedRoleDeleteResult.RETRY_SCHEDULED, result);
        ReconciliationState persisted = store.only();
        assertEquals("DELETE_PENDING", persisted.state());
        assertTrue(json.readTree(persisted.desiredStateJson()).path("delete").asBoolean());
    }

    @Test
    void deletePreservesPriorDisplayNameForShadowParity() throws Exception {
        FakeStore store = new FakeStore();
        PaperManagedRolePlatform platform = platform(store);
        ManagedRoleNamespace namespace = new ManagedRoleNamespace(LUMA_NAMESPACE);
        ManagedRoleKey key = new ManagedRoleKey(namespace, "guild:two");

        platform.clientFor(namespace).orElseThrow()
                .reconcile(new ManagedRoleClaim(
                        key,
                        "Guild Two",
                        Optional.of("1552390213500928122"),
                        Set.of(UUID.randomUUID())))
                .toCompletableFuture().join();
        platform.clientFor(namespace).orElseThrow()
                .delete(key)
                .toCompletableFuture().join();

        var desired = json.readTree(store.only().desiredStateJson());
        assertTrue(desired.path("delete").asBoolean());
        assertEquals("Guild Two", desired.path("displayName").asText());
        assertEquals("1552390213500928122", desired.path("existingDiscordRoleId").asText());
        assertEquals(0, desired.path("desiredMinecraftAccounts").size());
    }

    @Test
    void unavailableStorageIsReportedWithoutCreatingUnrestrictedClients() {
        PaperManagedRolePlatform platform = new PaperManagedRolePlatform(
                (PaperManagedRolePlatform.ClaimStoreProvider) () -> null,
                workers,
                json,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(DiscordPlatformAvailability.UNAVAILABLE, platform.availability());
        assertTrue(platform.clientFor(new ManagedRoleNamespace(LUMA_NAMESPACE)).isPresent());
    }

    private PaperManagedRolePlatform platform(FakeStore store) {
        return new PaperManagedRolePlatform(
                () -> store, workers, json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FakeStore implements PaperManagedRolePlatform.ClaimStore {
        private final Map<String, ReconciliationState> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<ReconciliationState> read(String key) {
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now) {
            ReconciliationState current = rows.get(state.reconciliationKey());
            long currentRevision = current == null ? -1L : current.revision();
            if (currentRevision != expectedRevision) {
                throw new IllegalStateException("revision conflict");
            }
            long nextRevision = expectedRevision + 1L;
            ReconciliationState stored = new ReconciliationState(
                    state.reconciliationKey(), state.resourceType(), state.resourceId(),
                    state.desiredStateJson(), state.observedStateJson(), state.state(),
                    state.attemptCount(), state.nextAttemptAt(), state.lastErrorCode(), nextRevision
            );
            rows.put(stored.reconciliationKey(), stored);
            return stored;
        }

        ReconciliationState only() {
            assertEquals(1, rows.size());
            return rows.values().iterator().next();
        }
    }
}

package net.enthusia.staff.paper.discordplatform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import net.enthusia.discord.platform.api.DiscordPlatformAvailability;
import net.enthusia.discord.platform.api.ManagedRoleClaim;
import net.enthusia.discord.platform.api.ManagedRoleClient;
import net.enthusia.discord.platform.api.ManagedRoleDeleteResult;
import net.enthusia.discord.platform.api.ManagedRoleKey;
import net.enthusia.discord.platform.api.ManagedRoleNamespace;
import net.enthusia.discord.platform.api.ManagedRolePlatform;
import net.enthusia.discord.platform.api.ManagedRoleReconcileResult;
import net.enthusia.discord.platform.api.ManagedRoleReconcileStatus;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;

/**
 * Paper-side managed-role claim publisher.
 *
 * <p>Consumers publish complete Minecraft membership snapshots. This runtime only persists
 * provider-neutral desired state; StaffBot remains the sole Discord/JDA owner.</p>
 */
public final class PaperManagedRolePlatform implements ManagedRolePlatform {
    static final String RESOURCE_TYPE = "MANAGED_ROLE";
    private static final Set<String> ALLOWED_NAMESPACES = Set.of("luma-guilds", "playtime-numerals");
    private static final int MAX_PERSIST_ATTEMPTS = 3;

    interface ClaimStore {
        Optional<ReconciliationState> read(String key);

        ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now);
    }

    private final Supplier<ClaimStore> store;
    private final ExecutorService workers;
    private final ObjectMapper json;
    private final Clock clock;

    public PaperManagedRolePlatform(
            Supplier<DataSource> dataSource,
            ExecutorService workers,
            ObjectMapper json,
            Clock clock
    ) {
        this(() -> {
            DataSource current = dataSource.get();
            return current == null ? null : new JdbcClaimStore(current);
        }, workers, json, clock);
    }

    PaperManagedRolePlatform(
            Supplier<ClaimStore> store,
            ExecutorService workers,
            ObjectMapper json,
            Clock clock
    ) {
        if (store == null || workers == null || json == null || clock == null) {
            throw new IllegalArgumentException("managed-role platform dependencies must be present");
        }
        this.store = store;
        this.workers = workers;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public int apiVersion() {
        return ManagedRolePlatform.API_VERSION;
    }

    @Override
    public DiscordPlatformAvailability availability() {
        return store.get() == null
                ? DiscordPlatformAvailability.UNAVAILABLE
                : DiscordPlatformAvailability.AVAILABLE;
    }

    @Override
    public Optional<ManagedRoleClient> clientFor(ManagedRoleNamespace namespace) {
        if (namespace == null || !ALLOWED_NAMESPACES.contains(namespace.value())) {
            return Optional.empty();
        }
        return Optional.of(new Client(namespace));
    }

    private final class Client implements ManagedRoleClient {
        private final ManagedRoleNamespace namespace;

        private Client(ManagedRoleNamespace namespace) {
            this.namespace = namespace;
        }

        @Override
        public ManagedRoleNamespace namespace() {
            return namespace;
        }

        @Override
        public DiscordPlatformAvailability availability() {
            return PaperManagedRolePlatform.this.availability();
        }

        @Override
        public CompletionStage<ManagedRoleReconcileResult> reconcile(ManagedRoleClaim claim) {
            if (claim == null || !namespace.equals(claim.key().namespace())) {
                return CompletableFuture.completedFuture(new ManagedRoleReconcileResult(
                        ManagedRoleReconcileStatus.REJECTED, 0, 0, 0, 0));
            }
            DesiredState desired = DesiredState.claim(claim);
            return submit(
                    () -> {
                        persist(desired, "PENDING");
                        return new ManagedRoleReconcileResult(
                                ManagedRoleReconcileStatus.RETRY_SCHEDULED,
                                claim.desiredMinecraftAccounts().size(), 0, 0, 0);
                    },
                    new ManagedRoleReconcileResult(
                            ManagedRoleReconcileStatus.UNAVAILABLE,
                            claim.desiredMinecraftAccounts().size(), 0, 0, 0)
            );
        }

        @Override
        public CompletionStage<ManagedRoleDeleteResult> delete(ManagedRoleKey key) {
            if (key == null || !namespace.equals(key.namespace())) {
                return CompletableFuture.completedFuture(ManagedRoleDeleteResult.REJECTED);
            }
            return submit(
                    () -> {
                        persist(DesiredState.delete(key), "DELETE_PENDING");
                        return ManagedRoleDeleteResult.RETRY_SCHEDULED;
                    },
                    ManagedRoleDeleteResult.UNAVAILABLE
            );
        }
    }

    private <T> CompletionStage<T> submit(java.util.concurrent.Callable<T> operation, T unavailable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            workers.execute(() -> {
                try {
                    future.complete(operation.call());
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                } catch (Exception exception) {
                    future.completeExceptionally(new IllegalStateException("managed-role operation failed", exception));
                }
            });
        } catch (RejectedExecutionException exception) {
            future.complete(unavailable);
        }
        return future;
    }

    private void persist(DesiredState desired, String stateName) {
        ClaimStore currentStore = store.get();
        if (currentStore == null) {
            throw new IllegalStateException("managed-role persistence is unavailable");
        }
        Instant now = clock.instant();
        String key = reconciliationKey(desired.namespace(), desired.localKey());
        String desiredJson = encode(desired);
        RuntimeException last = null;
        for (int attempt = 0; attempt < MAX_PERSIST_ATTEMPTS; attempt++) {
            Optional<ReconciliationState> current = currentStore.read(key);
            long expected = current.map(ReconciliationState::revision).orElse(-1L);
            ReconciliationState proposed = new ReconciliationState(
                    key,
                    RESOURCE_TYPE,
                    resourceId(desired.namespace(), desired.localKey()),
                    desiredJson,
                    current.flatMap(ReconciliationState::observedStateJson),
                    stateName,
                    0,
                    Optional.of(now),
                    Optional.empty(),
                    Math.max(0L, expected)
            );
            try {
                currentStore.save(proposed, expected, now);
                return;
            } catch (RuntimeException exception) {
                last = exception;
            }
        }
        throw new IllegalStateException("managed-role claim could not be persisted after revision retries", last);
    }

    private String encode(DesiredState desired) {
        try {
            return json.writeValueAsString(desired);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("managed-role desired state could not be encoded", exception);
        }
    }

    static String reconciliationKey(String namespace, String localKey) {
        return "managed-role:" + digest(namespace + "\n" + localKey);
    }

    static String resourceId(String namespace, String localKey) {
        return digest(namespace + "\n" + localKey);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    record DesiredState(
            int version,
            String namespace,
            String localKey,
            String displayName,
            List<String> desiredMinecraftAccounts,
            boolean delete
    ) {
        private static DesiredState claim(ManagedRoleClaim claim) {
            List<String> accounts = claim.desiredMinecraftAccounts().stream()
                    .sorted(Comparator.comparing(UUID::toString))
                    .map(UUID::toString)
                    .toList();
            return new DesiredState(
                    1,
                    claim.key().namespace().value(),
                    claim.key().localKey(),
                    claim.displayName(),
                    accounts,
                    false
            );
        }

        private static DesiredState delete(ManagedRoleKey key) {
            return new DesiredState(
                    1,
                    key.namespace().value(),
                    key.localKey(),
                    "",
                    List.of(),
                    true
            );
        }
    }

    private static final class JdbcClaimStore implements ClaimStore {
        private final DataSource dataSource;
        private final JdbcDiscordModerationPersistenceStore store;

        private JdbcClaimStore(DataSource dataSource) {
            this.dataSource = dataSource;
            this.store = new JdbcDiscordModerationPersistenceStore(dataSource);
        }

        @Override
        public Optional<ReconciliationState> read(String key) {
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("""
                         SELECT reconciliation_key, resource_type, resource_id, desired_state_json,
                                observed_state_json, state, attempt_count, next_attempt_at,
                                last_error_code, revision
                         FROM discord_reconciliation_state
                         WHERE reconciliation_key = ?
                         """)) {
                statement.setString(1, key);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    var next = rows.getTimestamp("next_attempt_at");
                    return Optional.of(new ReconciliationState(
                            rows.getString("reconciliation_key"),
                            rows.getString("resource_type"),
                            rows.getString("resource_id"),
                            rows.getString("desired_state_json"),
                            Optional.ofNullable(rows.getString("observed_state_json")),
                            rows.getString("state"),
                            rows.getInt("attempt_count"),
                            Optional.ofNullable(next).map(java.sql.Timestamp::toInstant),
                            Optional.ofNullable(rows.getString("last_error_code")),
                            rows.getLong("revision")
                    ));
                }
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException("Unable to read managed-role reconciliation state", exception);
            }
        }

        @Override
        public ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now) {
            return store.saveReconciliation(state, expectedRevision, now);
        }
    }
}

package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.ports.FreezeStore;
import org.junit.jupiter.api.Test;

final class FreezeReconnectVerificationTest {
    private static final Instant NOW = Instant.parse("2026-09-29T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAYER = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR = UUID.fromString("82000000-0000-0000-0000-000000000001");

    @Test
    void reconnectClearsOfflineDeadlineBeforeResolvingFrozenState() {
        RecordingStore store = new RecordingStore(record(7L, NOW.plusSeconds(300)));
        FreezeManager manager = manager(store);

        manager.verify(PLAYER, "ReconnectTarget");

        assertTrue(manager.isRestricted(PLAYER));
        assertEquals(1, store.connectedCalls);
        assertEquals(7L, store.lastConnectedRevision);
        assertTrue(store.current.offlineExpiresAt().isEmpty());
        assertEquals(8L, store.current.revision());
    }

    @Test
    void reconnectRevisionRaceRetriesLatestAuthoritativeRevision() {
        RecordingStore store = new RecordingStore(record(11L, NOW.plusSeconds(300)));
        store.conflictFirstConnect = true;
        FreezeManager manager = manager(store);

        manager.verify(PLAYER, "ReconnectTarget");

        assertTrue(manager.isRestricted(PLAYER));
        assertEquals(2, store.connectedCalls);
        assertEquals(12L, store.lastConnectedRevision);
        assertTrue(store.current.offlineExpiresAt().isEmpty());
        assertEquals(13L, store.current.revision());
    }

    @Test
    void repeatedReconnectConflictLeavesVerificationFailClosed() {
        RecordingStore store = new RecordingStore(record(20L, NOW.plusSeconds(300)));
        store.alwaysConflict = true;
        FreezeManager manager = manager(store);

        manager.verify(PLAYER, "ReconnectTarget");

        assertTrue(manager.isRestricted(PLAYER));
        assertFalse(manager.isCurrentFrozen(PLAYER, 1L));
        assertEquals(2, store.connectedCalls);
    }

    private static FreezeManager manager(FreezeStore store) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return new FreezeManager(
                null,
                CLOCK,
                () -> store,
                directExecutor(),
                (playerId, operation, unavailable) -> unavailable.run(),
                Runnable::run,
                logger,
                message -> {
                }
        );
    }

    private static FreezeRecord record(long revision, Instant expiry) {
        return new FreezeRecord(
                PLAYER,
                ACTOR,
                "investigation",
                NOW.minusSeconds(60),
                Optional.ofNullable(expiry),
                false,
                revision
        );
    }

    private static ExecutorService directExecutor() {
        return proxy(ExecutorService.class, (method, arguments) -> {
            if ("execute".equals(method.getName())) {
                ((Runnable) arguments[0]).run();
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> invocation) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.apply(method, arguments)
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    private static final class RecordingStore implements FreezeStore {
        private FreezeRecord current;
        private boolean conflictFirstConnect;
        private boolean alwaysConflict;
        private int connectedCalls;
        private long lastConnectedRevision = -1L;

        private RecordingStore(FreezeRecord current) {
            this.current = current;
        }

        @Override
        public Optional<FreezeRecord> active(UUID playerId, Instant now) {
            return Optional.ofNullable(current);
        }

        @Override
        public Optional<FreezeRecord> readActive(UUID playerId, Instant now) {
            return Optional.ofNullable(current);
        }

        @Override
        public Optional<FreezeRecord> connected(UUID playerId, long expectedRevision, Instant now) {
            connectedCalls++;
            lastConnectedRevision = expectedRevision;
            if (alwaysConflict || conflictFirstConnect && connectedCalls == 1) {
                current = new FreezeRecord(
                        current.playerId(),
                        current.frozenBy(),
                        current.reason(),
                        current.frozenAt(),
                        current.offlineExpiresAt(),
                        current.keepActive(),
                        current.revision() + 1
                );
                return Optional.empty();
            }
            if (current == null || current.revision() != expectedRevision) {
                return Optional.empty();
            }
            current = new FreezeRecord(
                    current.playerId(),
                    current.frozenBy(),
                    current.reason(),
                    current.frozenAt(),
                    Optional.empty(),
                    current.keepActive(),
                    current.revision() + 1
            );
            return Optional.of(current);
        }

        @Override
        public Optional<FreezeRecord> disconnected(
                UUID playerId,
                long expectedRevision,
                Instant offlineExpiration,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FreezeRecord apply(UUID playerId, UUID actorId, String reason, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean release(UUID playerId, UUID actorId, String reason, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean keepActive(UUID playerId, UUID actorId, String reason, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FreezeRecord> listActive(Instant now, int limit) {
            return current == null ? List.of() : List.of(current);
        }
    }
}

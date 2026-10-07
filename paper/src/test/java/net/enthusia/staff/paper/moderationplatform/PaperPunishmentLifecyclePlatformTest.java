package net.enthusia.staff.paper.moderationplatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.enthusia.staff.moderation.api.PunishmentCategory;
import net.enthusia.staff.moderation.api.PunishmentLifecycleCursor;
import org.junit.jupiter.api.Test;

class PaperPunishmentLifecyclePlatformTest {
    private static final Instant NOW = Instant.parse("2026-10-07T05:00:00Z");

    @Test
    void normalizesStaffSanctionTypesForFirstPartyConsumers() {
        assertEquals(PunishmentCategory.WARN, PaperPunishmentLifecyclePlatform.category("WARNING"));
        assertEquals(PunishmentCategory.KICK, PaperPunishmentLifecyclePlatform.category("KICK"));
        assertEquals(PunishmentCategory.MUTE, PaperPunishmentLifecyclePlatform.category("PUBLIC_MUTE"));
        assertEquals(PunishmentCategory.BAN, PaperPunishmentLifecyclePlatform.category("NETWORK_IDENTITY_BAN"));
        assertEquals(PunishmentCategory.OTHER, PaperPunishmentLifecyclePlatform.category("CONTENT_REMOVAL"));
    }

    @Test
    void terminalAndNaturallyExpiredMuteOrBanStatesAreInactive() {
        assertFalse(PaperPunishmentLifecyclePlatform.active(
                PunishmentCategory.BAN, "REVOKED", Optional.empty(), NOW
        ));
        assertFalse(PaperPunishmentLifecyclePlatform.active(
                PunishmentCategory.MUTE, "ACTIVE", Optional.of(NOW.minusSeconds(1)), NOW
        ));
        assertTrue(PaperPunishmentLifecyclePlatform.active(
                PunishmentCategory.BAN, "ACTIVE", Optional.of(NOW.plusSeconds(1)), NOW
        ));
        assertTrue(PaperPunishmentLifecyclePlatform.active(
                PunishmentCategory.WARN, "APPLIED", Optional.of(NOW.minusSeconds(1)), NOW
        ));
    }

    @Test
    void snapshotStatementUsesQueryTimeoutBelowConsumerDeadline() throws Exception {
        AtomicInteger timeoutSeconds = new AtomicInteger();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("setQueryTimeout".equals(method.getName())) {
                        timeoutSeconds.set((Integer) args[0]);
                    }
                    return null;
                }
        );

        PaperPunishmentLifecyclePlatform.configureSnapshotStatement(statement);

        assertEquals(45, timeoutSeconds.get());
    }

    @Test
    void unavailableStorageFailsAsynchronouslyAndLimitIsBounded() {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            PaperPunishmentLifecyclePlatform platform = new PaperPunishmentLifecyclePlatform(
                    () -> null,
                    workers,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
            assertFalse(platform.available());
            CompletionException failure = assertThrows(
                    CompletionException.class,
                    () -> platform.readAfter(PunishmentLifecycleCursor.beginning(), 10)
                            .toCompletableFuture()
                            .join()
            );
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> platform.readAfter(PunishmentLifecycleCursor.beginning(), 0)
            );
        } finally {
            workers.shutdownNow();
        }
    }
}

package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.InvestigationFlag;
import net.enthusia.staff.persistence.JdbcInvestigationFlagStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InvestigationFlagPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("staff_investigation_test").withUsername("staff_test").withPassword(UUID.randomUUID().toString());

    @Test void upgradesV21RetainsFlagsAcrossRestartAndRollsBackAuditFailures() throws Exception {
        Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .locations("classpath:db/migration").target("21").load().migrate();
        UUID target = UUID.randomUUID(), actor = UUID.randomUUID(), id = UUID.randomUUID();
        var flag = new InvestigationFlag(id, target, actor, "watch", "Review next session", NOW, NOW.plusSeconds(3600), null);
        try (var runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            var store = new JdbcInvestigationFlagStore(runtime.dataSource());
            store.create(flag);
            assertEquals(1, store.active(target, NOW, 20).size());
            assertTrue(store.active(target, NOW.plusSeconds(3600), 20).isEmpty());
            assertEquals(1, auditCount(id));
        }
        try (var runtime = MariaDb.initialize(MariaDbIntegrationSupport.databaseConfig(DATABASE))) {
            var store = new JdbcInvestigationFlagStore(runtime.dataSource());
            assertEquals(flag, store.active(target, NOW, 20).getFirst());
            installAuditFailure();
            try {
                assertThrows(ModerationPersistenceException.class, () -> store.resolve(id, actor, "Reviewed", NOW.plusSeconds(10)));
                assertEquals(1, store.active(target, NOW, 20).size());
                var failed = new InvestigationFlag(UUID.randomUUID(), target, actor, "watch", "Forced failure", NOW, null, null);
                assertThrows(ModerationPersistenceException.class, () -> store.create(failed));
                assertEquals(1, store.active(target, NOW, 20).size());
                assertEquals(1, auditCount(id));
            } finally {
                try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE); Statement statement = connection.createStatement()) {
                    statement.execute("DROP TRIGGER IF EXISTS test_fail_flag_audit");
                }
            }
            assertTrue(store.resolve(id, actor, "Reviewed", NOW.plusSeconds(20)));
            assertFalse(store.resolve(id, actor, "Retry", NOW.plusSeconds(30)));
            assertTrue(store.active(target, NOW, 20).isEmpty());
            assertEquals(2, auditCount(id));
            Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                    .locations("classpath:db/migration").load().validate();
        }
    }

    private static void installAuditFailure() throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER test_fail_flag_audit BEFORE INSERT ON player_investigation_flag_audit
                    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced flag audit failure'
                    """);
        }
    }
    private static int auditCount(UUID flagId) throws Exception {
        try (Connection connection = MariaDbIntegrationSupport.connection(DATABASE);
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM player_investigation_flag_audit WHERE flag_id=?")) {
            statement.setBytes(1, MariaDbIntegrationSupport.uuidBytes(flagId));
            try (var result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }
}

package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.persistence.JdbcMinecraftBanDiscordNotificationStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.UuidBytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinecraftBanDiscordNotificationIntegrationTest {
    private static final Instant CUTOVER = Instant.parse("2026-10-05T05:00:00Z");
    private static final String DISCORD_ID = "1410303324745371709";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4.8")
            .withDatabaseName("enthusia_staff_minecraft_ban_dm")
            .withUsername("enthusia")
            .withPassword("enthusia-test-password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = open()) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void cutoverSkipsOldCasesAndDurablyQueuesOnlyLinkedFutureBans() throws Exception {
        UUID oldTarget = seedTarget("OldBanPlayer");
        UUID linkedTarget = seedTarget("LinkedBanPlayer");
        UUID unlinkedTarget = seedTarget("UnlinkedBanPlayer");
        seedLink(linkedTarget, DISCORD_ID, CUTOVER.minus(Duration.ofDays(1)));

        seedBan("DMOLD00000000001", oldTarget, CUTOVER.minusSeconds(1), null);
        JdbcMinecraftBanDiscordNotificationStore store;
        try (HikariDataSource dataSource = open()) {
            store = new JdbcMinecraftBanDiscordNotificationStore(dataSource);
            assertEquals(CUTOVER, store.ensureCutover(CUTOVER));
            assertEquals(CUTOVER, store.ensureCutover(CUTOVER.plusSeconds(5)));
        }

        seedBan("DMLINK0000000001", linkedTarget, CUTOVER.plusSeconds(1), CUTOVER.plus(Duration.ofDays(7)));
        seedBan("DMNONE0000000001", unlinkedTarget, CUTOVER.plusSeconds(2), null);

        try (HikariDataSource dataSource = open()) {
            store = new JdbcMinecraftBanDiscordNotificationStore(dataSource);
            assertEquals(2, store.mirrorEligible(CUTOVER.plusSeconds(3), 10));
            assertEquals(0, store.mirrorEligible(CUTOVER.plusSeconds(4), 10));

            List<JdbcMinecraftBanDiscordNotificationStore.Lease> work = store.claimDue(
                    CUTOVER.plusSeconds(4), 10, "integration-worker", CUTOVER.plusSeconds(34)
            );
            assertEquals(1, work.size());
            var lease = work.getFirst();
            assertEquals(DISCORD_ID, lease.discordUserId());
            assertEquals("LinkedBanPlayer", lease.minecraftName());
            assertEquals("Cheating", lease.publicReason());
            assertEquals(CUTOVER.plus(Duration.ofDays(7)), lease.expiresAt().orElseThrow());
            assertEquals(1, lease.attemptCount());

            store.markFailed(
                    lease,
                    true,
                    CUTOVER.plusSeconds(10),
                    "MINECRAFT_BAN_DM_RETRYABLE"
            );
            var retry = store.claimDue(
                    CUTOVER.plusSeconds(10), 10, "integration-worker-2", CUTOVER.plusSeconds(40)
            );
            assertEquals(1, retry.size());
            assertEquals(2, retry.getFirst().attemptCount());
            store.markDelivered(retry.getFirst(), CUTOVER.plusSeconds(11));

            assertTrue(store.claimDue(
                    CUTOVER.plusSeconds(60), 10, "integration-worker-3", CUTOVER.plusSeconds(90)
            ).isEmpty());
        }
    }

    private static UUID seedTarget(String username) throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        try (HikariDataSource dataSource = open(); Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement player = connection.prepareStatement("""
                    INSERT INTO players(
                        player_id, current_username, lowercase_username, platform,
                        first_seen_at, last_seen_at
                    ) VALUES (?, ?, ?, 'JAVA', ?, ?)
                    """);
                 PreparedStatement subject = connection.prepareStatement("""
                    INSERT INTO moderation_subjects(subject_id, created_at, updated_at)
                    VALUES (?, ?, ?)
                    """);
                 PreparedStatement identity = connection.prepareStatement("""
                    INSERT INTO moderation_subject_minecraft_identities(player_id, subject_id, linked_at)
                    VALUES (?, ?, ?)
                    """)) {
                Timestamp timestamp = Timestamp.from(CUTOVER.minus(Duration.ofDays(2)));
                player.setBytes(1, UuidBytes.toBytes(playerId));
                player.setString(2, username);
                player.setString(3, username.toLowerCase(java.util.Locale.ROOT));
                player.setTimestamp(4, timestamp);
                player.setTimestamp(5, timestamp);
                player.executeUpdate();

                subject.setBytes(1, UuidBytes.toBytes(subjectId));
                subject.setTimestamp(2, timestamp);
                subject.setTimestamp(3, timestamp);
                subject.executeUpdate();

                identity.setBytes(1, UuidBytes.toBytes(playerId));
                identity.setBytes(2, UuidBytes.toBytes(subjectId));
                identity.setTimestamp(3, timestamp);
                identity.executeUpdate();
            }
            connection.commit();
        }
        return playerId;
    }

    private static void seedLink(UUID playerId, String discordId, Instant linkedAt) throws Exception {
        try (HikariDataSource dataSource = open(); Connection connection = dataSource.getConnection();
             PreparedStatement subject = connection.prepareStatement("""
                     SELECT subject_id FROM moderation_subject_minecraft_identities WHERE player_id=?
                     """)) {
            subject.setBytes(1, UuidBytes.toBytes(playerId));
            byte[] subjectId;
            try (var result = subject.executeQuery()) {
                assertTrue(result.next());
                subjectId = result.getBytes(1);
            }
            try (PreparedStatement link = connection.prepareStatement("""
                    INSERT INTO discord_minecraft_links(
                        link_id, operation_key, subject_id, discord_user_id,
                        minecraft_player_id, linked_at, source
                    ) VALUES (?, ?, ?, ?, ?, ?, 'STAFF_RECOVERY')
                    """)) {
                link.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                link.setString(2, "dm-link-" + UUID.randomUUID());
                link.setBytes(3, subjectId);
                link.setBigDecimal(4, new BigDecimal(discordId));
                link.setBytes(5, UuidBytes.toBytes(playerId));
                link.setTimestamp(6, Timestamp.from(linkedAt));
                link.executeUpdate();
            }
        }
    }

    private static void seedBan(String caseId, UUID target, Instant issuedAt, Instant expiresAt) throws Exception {
        try (HikariDataSource dataSource = open(); Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement moderationCase = connection.prepareStatement("""
                    INSERT INTO cases(
                        case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                        public_reason, exact_reason_id, sanction_family, internal_explanation,
                        configuration_version, visibility, issued_at
                    ) VALUES (?, ?, ?, ?, 'IntegrationStaff', 'ADMIN', 'Cheating',
                        'cheating.client', 'cheating', 'Private evidence', 'test-v1', 'PUBLIC', ?)
                    """);
                 PreparedStatement sanction = connection.prepareStatement("""
                    INSERT INTO sanctions(
                        sanction_id, case_id, target_id, sanction_type, status,
                        issued_at, activated_at, expiration_at
                    ) VALUES (?, ?, ?, 'BAN', 'ACTIVE', ?, ?, ?)
                    """)) {
                moderationCase.setString(1, caseId);
                moderationCase.setString(2, "dm-case-" + caseId);
                moderationCase.setBytes(3, UuidBytes.toBytes(target));
                moderationCase.setBytes(4, UuidBytes.toBytes(UUID.randomUUID()));
                moderationCase.setTimestamp(5, Timestamp.from(issuedAt));
                moderationCase.executeUpdate();

                sanction.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
                sanction.setString(2, caseId);
                sanction.setBytes(3, UuidBytes.toBytes(target));
                Timestamp timestamp = Timestamp.from(issuedAt);
                sanction.setTimestamp(4, timestamp);
                sanction.setTimestamp(5, timestamp);
                sanction.setTimestamp(6, expiresAt == null ? null : Timestamp.from(expiresAt));
                sanction.executeUpdate();
            }
            connection.commit();
        }
    }

    private static HikariDataSource open() {
        return MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }
}

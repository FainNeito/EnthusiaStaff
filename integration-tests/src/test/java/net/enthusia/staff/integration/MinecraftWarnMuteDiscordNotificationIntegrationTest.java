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
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.JdbcMinecraftWarnMuteDiscordNotificationStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.UuidBytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinecraftWarnMuteDiscordNotificationIntegrationTest {
    private static final Instant CUTOVER = Instant.parse("2026-10-06T12:00:00Z");
    private static final String DISCORD_ID = "1410303324745371709";

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4.8")
            .withDatabaseName("enthusia_staff_minecraft_warn_mute_dm")
            .withUsername("enthusia")
            .withPassword("enthusia-test-password");

    @BeforeAll
    static void migrate() {
        try (HikariDataSource dataSource = open()) {
            MariaDb.migrate(dataSource);
        }
    }

    @Test
    void cutoverQueuesOnlyLinkedFutureWarningsAndMutesWithoutDuplicatingBanPath() throws Exception {
        Fixture fixture = seedFixture();

        JdbcMinecraftWarnMuteDiscordNotificationStore store;
        try (HikariDataSource dataSource = open()) {
            store = new JdbcMinecraftWarnMuteDiscordNotificationStore(dataSource);
            assertEquals(CUTOVER, store.ensureCutover(CUTOVER));
            assertEquals(CUTOVER, store.ensureCutover(CUTOVER.plusSeconds(5)));
        }

        seedFuturePunishments(fixture);
        verifyQueuedNotifications(fixture.muteExpiry());
    }

    private static Fixture seedFixture() throws Exception {
        UUID oldTarget = seedTarget("OldWarningPlayer");
        UUID muteTarget = seedTarget("LinkedMutePlayer");
        UUID warningTarget = seedTarget("LinkedWarningPlayer");
        UUID unlinkedTarget = seedTarget("UnlinkedMutePlayer");
        UUID banTarget = seedTarget("BanAndWarningPlayer");

        seedLink(muteTarget, DISCORD_ID, CUTOVER.minus(Duration.ofDays(1)));
        seedLink(warningTarget, DISCORD_ID, CUTOVER.minus(Duration.ofDays(1)));
        seedLink(banTarget, DISCORD_ID, CUTOVER.minus(Duration.ofDays(1)));

        seedCase("WMOLD00000000001", oldTarget, CUTOVER.minusSeconds(1), "Old warning");
        seedSanction("WMOLD00000000001", oldTarget, "WARNING", "APPLIED", CUTOVER.minusSeconds(1), null);
        return new Fixture(
                muteTarget,
                warningTarget,
                unlinkedTarget,
                banTarget,
                CUTOVER.plus(Duration.ofHours(6))
        );
    }

    private static void seedFuturePunishments(Fixture fixture) throws Exception {
        seedCase("WMMUTE0000000001", fixture.muteTarget(), CUTOVER.plusSeconds(1), "Repeated spam");
        seedSanction(
                "WMMUTE0000000001", fixture.muteTarget(), "MUTE", "ACTIVE",
                CUTOVER.plusSeconds(1), fixture.muteExpiry());

        seedCase("WMWARN0000000001", fixture.warningTarget(), CUTOVER.plusSeconds(2), "Chat spam");
        seedSanction(
                "WMWARN0000000001", fixture.warningTarget(), "WARNING", "APPLIED",
                CUTOVER.plusSeconds(2), null);

        seedCase("WMNONE0000000001", fixture.unlinkedTarget(), CUTOVER.plusSeconds(3), "Mute reason");
        seedSanction(
                "WMNONE0000000001", fixture.unlinkedTarget(), "MUTE", "ACTIVE",
                CUTOVER.plusSeconds(3), fixture.muteExpiry());

        seedCase("WMBAN00000000001", fixture.banTarget(), CUTOVER.plusSeconds(4), "Combined serious case");
        seedSanction(
                "WMBAN00000000001", fixture.banTarget(), "WARNING", "APPLIED",
                CUTOVER.plusSeconds(4), null);
        seedSanction(
                "WMBAN00000000001", fixture.banTarget(), "BAN", "ACTIVE",
                CUTOVER.plusSeconds(4), null);
    }

    private static void verifyQueuedNotifications(Instant muteExpiry) {
        try (HikariDataSource dataSource = open()) {
            JdbcMinecraftWarnMuteDiscordNotificationStore store =
                    new JdbcMinecraftWarnMuteDiscordNotificationStore(dataSource);
            assertEquals(3, store.mirrorEligible(CUTOVER.plusSeconds(5), 10));
            assertEquals(0, store.mirrorEligible(CUTOVER.plusSeconds(6), 10));

            List<JdbcMinecraftWarnMuteDiscordNotificationStore.Lease> work = store.claimDue(
                    CUTOVER.plusSeconds(6), 10, "integration-worker", CUTOVER.plusSeconds(36));
            assertEquals(2, work.size());
            var mute = lease(work, SanctionType.MUTE);
            assertEquals(DISCORD_ID, mute.discordUserId());
            assertEquals("LinkedMutePlayer", mute.minecraftName());
            assertEquals("Repeated spam", mute.publicReason());
            assertEquals(muteExpiry, mute.expiresAt().orElseThrow());
            assertEquals(1, mute.attemptCount());

            var warning = lease(work, SanctionType.WARNING);
            assertEquals("LinkedWarningPlayer", warning.minecraftName());
            assertTrue(warning.expiresAt().isEmpty());
            settleAndVerifyRetry(store, warning, mute);
        }
    }

    private static JdbcMinecraftWarnMuteDiscordNotificationStore.Lease lease(
            List<JdbcMinecraftWarnMuteDiscordNotificationStore.Lease> work,
            SanctionType type
    ) {
        return work.stream().filter(value -> value.type() == type).findFirst().orElseThrow();
    }

    private static void settleAndVerifyRetry(
            JdbcMinecraftWarnMuteDiscordNotificationStore store,
            JdbcMinecraftWarnMuteDiscordNotificationStore.Lease warning,
            JdbcMinecraftWarnMuteDiscordNotificationStore.Lease mute
    ) {
        store.markDelivered(warning, CUTOVER.plusSeconds(7));
        store.markFailed(
                mute, true, CUTOVER.plusSeconds(10), "MINECRAFT_WARN_MUTE_DM_RETRYABLE");

        var retry = store.claimDue(
                CUTOVER.plusSeconds(10), 10, "integration-worker-2", CUTOVER.plusSeconds(40));
        assertEquals(1, retry.size());
        assertEquals(SanctionType.MUTE, retry.getFirst().type());
        assertEquals(2, retry.getFirst().attemptCount());
        store.markDelivered(retry.getFirst(), CUTOVER.plusSeconds(11));

        assertTrue(store.claimDue(
                CUTOVER.plusSeconds(60), 10, "integration-worker-3", CUTOVER.plusSeconds(90)
        ).isEmpty());
    }

    private record Fixture(
            UUID muteTarget,
            UUID warningTarget,
            UUID unlinkedTarget,
            UUID banTarget,
            Instant muteExpiry
    ) {
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
                link.setString(2, "warn-mute-link-" + UUID.randomUUID());
                link.setBytes(3, subjectId);
                link.setBigDecimal(4, new BigDecimal(discordId));
                link.setBytes(5, UuidBytes.toBytes(playerId));
                link.setTimestamp(6, Timestamp.from(linkedAt));
                link.executeUpdate();
            }
        }
    }

    private static void seedCase(
            String caseId,
            UUID target,
            Instant issuedAt,
            String publicReason
    ) throws Exception {
        try (HikariDataSource dataSource = open(); Connection connection = dataSource.getConnection();
             PreparedStatement moderationCase = connection.prepareStatement("""
                    INSERT INTO cases(
                        case_id, idempotency_key, target_id, actor_id, actor_name, actor_rank,
                        public_reason, exact_reason_id, sanction_family, internal_explanation,
                        configuration_version, visibility, issued_at
                    ) VALUES (?, ?, ?, ?, 'IntegrationStaff', 'ADMIN', ?,
                        'chat.test', 'chat', 'Private evidence', 'test-v1', 'PUBLIC', ?)
                    """)) {
            moderationCase.setString(1, caseId);
            moderationCase.setString(2, "warn-mute-case-" + caseId);
            moderationCase.setBytes(3, UuidBytes.toBytes(target));
            moderationCase.setBytes(4, UuidBytes.toBytes(UUID.randomUUID()));
            moderationCase.setString(5, publicReason);
            moderationCase.setTimestamp(6, Timestamp.from(issuedAt));
            moderationCase.executeUpdate();
        }
    }

    private static void seedSanction(
            String caseId,
            UUID target,
            String type,
            String status,
            Instant issuedAt,
            Instant expiresAt
    ) throws Exception {
        try (HikariDataSource dataSource = open(); Connection connection = dataSource.getConnection();
             PreparedStatement sanction = connection.prepareStatement("""
                    INSERT INTO sanctions(
                        sanction_id, case_id, target_id, sanction_type, status,
                        issued_at, activated_at, expiration_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
            sanction.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            sanction.setString(2, caseId);
            sanction.setBytes(3, UuidBytes.toBytes(target));
            sanction.setString(4, type);
            sanction.setString(5, status);
            Timestamp timestamp = Timestamp.from(issuedAt);
            sanction.setTimestamp(6, timestamp);
            sanction.setTimestamp(7, timestamp);
            sanction.setTimestamp(8, expiresAt == null ? null : Timestamp.from(expiresAt));
            sanction.executeUpdate();
        }
    }

    private static HikariDataSource open() {
        return MariaDb.open(MariaDbIntegrationSupport.databaseConfig(DATABASE));
    }
}

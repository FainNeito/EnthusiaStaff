package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariDataSource;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.enthusia.staff.domain.application.AccountLinkingService;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.JdbcAccountLinkingStore;
import net.enthusia.staff.persistence.JdbcDiscordModerationPersistenceStore;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.UuidBytes;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class WebsiteAccountReaderMariaDbIntegrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8.3")
            .withDatabaseName("website_account_contract")
            .withUsername("website_test")
            .withPassword("website_test_password");

    @Test
    void websiteChallengeReadsOnlyItsOwnersRealLinkedAccount() throws Exception {
        var config = new DatabaseConfig(DATABASE.getJdbcUrl().replace("jdbc:mysql:", "jdbc:mariadb:"),
                DATABASE.getUsername(), DATABASE.getPassword(), 4, 5_000);
        Instant now = Instant.parse("2026-10-12T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        UUID player = UUID.randomUUID();
        DiscordUserId owner = new DiscordUserId("111111111111111111");
        DiscordUserId other = new DiscordUserId("222222222222222222");
        try (HikariDataSource dataSource = MariaDb.open(config)) {
            MariaDb.migrate(dataSource);
            try (var connection = dataSource.getConnection(); var insert = connection.prepareStatement("""
                    INSERT INTO players(player_id, current_username, lowercase_username, platform, first_seen_at, last_seen_at)
                    VALUES (?, 'Builder', 'builder', 'JAVA', ?, ?)
                    """)) {
                insert.setBytes(1, UuidBytes.toBytes(player));
                insert.setTimestamp(2, Timestamp.from(now));
                insert.setTimestamp(3, Timestamp.from(now));
                insert.executeUpdate();
            }
            var identities = new JdbcDiscordModerationPersistenceStore(dataSource);
            var links = new JdbcAccountLinkingStore(dataSource);
            identities.ensureMinecraftSubject(player, now);
            var reader = new WebsiteAccountReader(dataSource, clock, id ->
                    AccountLinkingService.issueFromDiscord(clock, new SecureRandom(), identities, links, id));
            Map<String, Object> challenge = reader.start(owner.value());
            String requestId = (String) challenge.get("requestId");
            assertEquals("WAITING_FOR_MINECRAFT", reader.status(owner.value(), requestId).get("status"));
            assertEquals("EXPIRED_OR_USED", reader.status(other.value(), requestId).get("status"));
            links.completeFromMinecraft(requestId, player, "website-contract-operation", now.plusSeconds(1));
            assertEquals("LINKED", reader.status(owner.value(), requestId).get("status"));
            assertEquals(List.of(Map.of("uuid", player.toString(), "name", "Builder", "linkedAt", now.plusSeconds(1).toString())),
                    reader.read(owner.value()).get("accounts"));
            assertEquals(List.of(), reader.read(other.value()).get("accounts"));
        }
    }
}

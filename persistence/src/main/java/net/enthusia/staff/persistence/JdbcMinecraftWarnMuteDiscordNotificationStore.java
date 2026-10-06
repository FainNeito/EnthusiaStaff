package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.sanction.SanctionType;

/**
 * Durable StaffBot queue for linked-player Discord notifications about newly committed Minecraft
 * warnings and mutes.
 *
 * <p>This intentionally owns a cutover separate from the existing ban notification queue so
 * enabling warning/mute delivery never backfills historical punishments.</p>
 */
public final class JdbcMinecraftWarnMuteDiscordNotificationStore {
    private static final String DESTINATION = "player-dm";
    private static final String EVENT = "MINECRAFT_WARN_MUTE_NOTIFY";
    private static final String CUTOVER_EVENT = "PLAYER_DM_WARN_MUTE_CUTOVER";
    private static final String CUTOVER_KEY = "staffbot:player-dm-warn-mute-cutover:v1";
    private static final int MAX_LIMIT = 100;
    private static final String SELECT_DUE_SQL = """
            SELECT d.message_id, d.attempt_count,
                   JSON_UNQUOTE(JSON_EXTRACT(d.payload_json, '$.discordUserId')) AS discord_user_id,
                   JSON_UNQUOTE(JSON_EXTRACT(d.payload_json, '$.punishmentType')) AS punishment_type,
                   c.case_id, c.public_reason, c.issued_at,
                   COALESCE(p.current_username, 'Minecraft account') AS minecraft_name,
                   CASE
                       WHEN JSON_UNQUOTE(JSON_EXTRACT(d.payload_json, '$.punishmentType')) = 'MUTE'
                       THEN EXISTS(
                           SELECT 1 FROM sanctions active
                           WHERE active.case_id=c.case_id
                             AND active.sanction_type IN ('MUTE','PUBLIC_MUTE')
                             AND active.status='ACTIVE'
                             AND (active.expiration_at IS NULL OR active.expiration_at > ?)
                       )
                       WHEN JSON_UNQUOTE(JSON_EXTRACT(d.payload_json, '$.punishmentType')) = 'WARNING'
                       THEN EXISTS(
                           SELECT 1 FROM sanctions warning
                           WHERE warning.case_id=c.case_id
                             AND warning.sanction_type='WARNING'
                             AND warning.status IN ('APPLIED','ACTIVE')
                       )
                       ELSE FALSE
                   END AS punishment_active,
                   (
                       SELECT active_expiry.expiration_at
                       FROM sanctions active_expiry
                       WHERE active_expiry.case_id=c.case_id
                         AND active_expiry.sanction_type IN ('MUTE','PUBLIC_MUTE')
                         AND active_expiry.status='ACTIVE'
                         AND (active_expiry.expiration_at IS NULL OR active_expiry.expiration_at > ?)
                       ORDER BY FIELD(active_expiry.sanction_type, 'MUTE','PUBLIC_MUTE')
                       LIMIT 1
                   ) AS expiration_at
            FROM discord_outbox d
            JOIN cases c
              ON c.case_id=JSON_UNQUOTE(JSON_EXTRACT(d.payload_json, '$.caseId'))
            JOIN players p ON p.player_id=c.target_id
            WHERE d.destination=? AND d.event_type=?
              AND d.available_at <= ?
              AND (d.state='PENDING' OR (d.state='LEASED' AND d.lease_until <= ?))
            ORDER BY d.available_at, d.created_at
            LIMIT ? FOR UPDATE SKIP LOCKED
            """;
    private static final String SELECT_CANDIDATES_SQL = """
            SELECT c.case_id, c.issued_at,
                   CASE
                       WHEN EXISTS (
                           SELECT 1 FROM sanctions mute
                           WHERE mute.case_id=c.case_id
                             AND mute.sanction_type IN ('MUTE','PUBLIC_MUTE')
                             AND mute.status='ACTIVE'
                             AND (mute.expiration_at IS NULL OR mute.expiration_at > ?)
                       ) THEN 'MUTE'
                       ELSE 'WARNING'
                   END AS punishment_type,
                   (
                       SELECT l.discord_user_id
                       FROM discord_minecraft_links l
                       WHERE l.minecraft_player_id=c.target_id
                         AND l.linked_at <= c.issued_at
                         AND (l.unlinked_at IS NULL OR l.unlinked_at > c.issued_at)
                       ORDER BY l.linked_at DESC
                       LIMIT 1
                   ) AS discord_user_id
            FROM cases c
            WHERE c.issued_at >= ?
              AND NOT EXISTS (
                  SELECT 1 FROM sanctions banned
                  WHERE banned.case_id=c.case_id
                    AND banned.sanction_type IN ('BAN','NETWORK_BAN','NETWORK_IDENTITY_BAN')
              )
              AND (
                  EXISTS (
                      SELECT 1 FROM sanctions mute
                      WHERE mute.case_id=c.case_id
                        AND mute.sanction_type IN ('MUTE','PUBLIC_MUTE')
                        AND mute.status='ACTIVE'
                        AND (mute.expiration_at IS NULL OR mute.expiration_at > ?)
                  )
                  OR EXISTS (
                      SELECT 1 FROM sanctions warning
                      WHERE warning.case_id=c.case_id
                        AND warning.sanction_type='WARNING'
                        AND warning.status IN ('APPLIED','ACTIVE')
                  )
              )
              AND NOT EXISTS (
                  SELECT 1 FROM discord_outbox d
                  WHERE d.idempotency_key=CONCAT('case:', c.case_id, ':player-dm-warn-mute')
              )
            ORDER BY c.issued_at, c.case_id
            LIMIT ?
            """;

    private final DataSource dataSource;

    public JdbcMinecraftWarnMuteDiscordNotificationStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("data source must be present");
        }
        this.dataSource = dataSource;
    }

    public Instant ensureCutover(Instant now) {
        requireInstant(now);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to initialize Minecraft warning/mute Discord notification cutover",
                connection -> ensureCutover(connection, now)
        );
    }

    public int mirrorEligible(Instant now, int limit) {
        requireInstant(now);
        requireLimit(limit);
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to mirror Minecraft warning/mute Discord notifications",
                connection -> mirrorEligible(connection, now, limit)
        );
    }

    public List<Lease> claimDue(Instant now, int limit, String owner, Instant leaseUntil) {
        requireInstant(now);
        requireInstant(leaseUntil);
        requireLimit(limit);
        requireOwner(owner);
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("lease expiration must be after claim time");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to claim Minecraft warning/mute Discord notifications",
                connection -> claimDue(connection, now, limit, owner, leaseUntil)
        );
    }

    public void markDelivered(Lease lease, Instant now) {
        requireLease(lease);
        requireInstant(now);
        JdbcTransactionSupport.execute(
                dataSource,
                "Unable to mark Minecraft warning/mute Discord notification delivered",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE discord_outbox
                            SET state='DELIVERED', delivered_at=?, lease_owner=NULL, lease_until=NULL,
                                last_error_code=NULL
                            WHERE message_id=? AND destination=? AND event_type=?
                              AND state='LEASED' AND lease_owner=?
                            """)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setBytes(2, UuidBytes.toBytes(lease.messageId()));
                        statement.setString(3, DESTINATION);
                        statement.setString(4, EVENT);
                        statement.setString(5, lease.owner());
                        JdbcTransactionSupport.requireSingleUpdate(
                                statement.executeUpdate(),
                                "Minecraft warning/mute Discord notification lease changed before delivery"
                        );
                    }
                    return null;
                }
        );
    }

    public void markFailed(Lease lease, boolean retry, Instant availableAt, String errorCode) {
        requireLease(lease);
        requireInstant(availableAt);
        if (errorCode == null || errorCode.isBlank() || errorCode.length() > 64) {
            throw new IllegalArgumentException("notification error code is invalid");
        }
        JdbcTransactionSupport.execute(
                dataSource,
                "Unable to settle Minecraft warning/mute Discord notification failure",
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE discord_outbox
                            SET state=?, available_at=?, lease_owner=NULL, lease_until=NULL,
                                last_error_code=?, delivered_at=NULL
                            WHERE message_id=? AND destination=? AND event_type=?
                              AND state='LEASED' AND lease_owner=?
                            """)) {
                        statement.setString(1, retry ? "PENDING" : "DEAD_LETTER");
                        statement.setTimestamp(2, Timestamp.from(availableAt));
                        statement.setString(3, errorCode);
                        statement.setBytes(4, UuidBytes.toBytes(lease.messageId()));
                        statement.setString(5, DESTINATION);
                        statement.setString(6, EVENT);
                        statement.setString(7, lease.owner());
                        JdbcTransactionSupport.requireSingleUpdate(
                                statement.executeUpdate(),
                                "Minecraft warning/mute Discord notification lease changed before failure settlement"
                        );
                    }
                    return null;
                }
        );
    }

    private static Instant ensureCutover(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT created_at
                FROM discord_outbox
                WHERE idempotency_key=?
                FOR UPDATE
                """)) {
            select.setString(1, CUTOVER_KEY);
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    return result.getTimestamp(1).toInstant();
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO discord_outbox(
                    message_id, idempotency_key, destination, event_type, payload_json,
                    state, attempt_count, available_at, created_at, delivered_at
                ) VALUES (?, ?, ?, ?, JSON_OBJECT('version', 1), 'DELIVERED', 0, ?, ?, ?)
                """)) {
            insert.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            insert.setString(2, CUTOVER_KEY);
            insert.setString(3, DESTINATION);
            insert.setString(4, CUTOVER_EVENT);
            Timestamp timestamp = Timestamp.from(now);
            insert.setTimestamp(5, timestamp);
            insert.setTimestamp(6, timestamp);
            insert.setTimestamp(7, timestamp);
            insert.executeUpdate();
        }
        return now;
    }

    private static int mirrorEligible(Connection connection, Instant now, int limit) throws SQLException {
        Instant cutover = ensureCutover(connection, now);
        List<Candidate> candidates = selectCandidates(connection, cutover, now, limit);
        int inserted = 0;
        for (Candidate candidate : candidates) {
            inserted += insertCandidate(connection, candidate, now);
        }
        return inserted;
    }

    private static List<Candidate> selectCandidates(
            Connection connection,
            Instant cutover,
            Instant now,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CANDIDATES_SQL)) {
            bindCandidateQuery(statement, cutover, now, limit);
            try (ResultSet result = statement.executeQuery()) {
                return readCandidates(result);
            }
        }
    }

    private static void bindCandidateQuery(
            PreparedStatement statement,
            Instant cutover,
            Instant now,
            int limit
    ) throws SQLException {
        statement.setTimestamp(1, Timestamp.from(now));
        statement.setTimestamp(2, Timestamp.from(cutover));
        statement.setTimestamp(3, Timestamp.from(now));
        statement.setInt(4, limit);
    }

    private static List<Candidate> readCandidates(ResultSet result) throws SQLException {
        List<Candidate> candidates = new ArrayList<>();
        while (result.next()) {
            candidates.add(new Candidate(
                    result.getString("case_id"),
                    result.getTimestamp("issued_at").toInstant(),
                    SanctionType.valueOf(result.getString("punishment_type")),
                    unsignedText(result.getBigDecimal("discord_user_id"))
            ));
        }
        return candidates;
    }

    private static int insertCandidate(Connection connection, Candidate candidate, Instant now) throws SQLException {
        boolean linked = candidate.discordUserId().isPresent();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO discord_outbox(
                    message_id, idempotency_key, destination, event_type, payload_json,
                    state, attempt_count, available_at, created_at, delivered_at
                ) VALUES (?, ?, ?, ?, JSON_OBJECT(
                    'caseId', ?, 'discordUserId', ?, 'punishmentType', ?, 'skipReason', ?),
                    ?, 0, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, "case:" + candidate.caseId() + ":player-dm-warn-mute");
            statement.setString(3, DESTINATION);
            statement.setString(4, EVENT);
            statement.setString(5, candidate.caseId());
            if (linked) {
                statement.setString(6, candidate.discordUserId().orElseThrow());
            } else {
                statement.setNull(6, java.sql.Types.VARCHAR);
            }
            statement.setString(7, candidate.type().name());
            if (linked) {
                statement.setNull(8, java.sql.Types.VARCHAR);
            } else {
                statement.setString(8, "unlinked-at-punishment");
            }
            statement.setString(9, linked ? "PENDING" : "DELIVERED");
            statement.setTimestamp(10, Timestamp.from(now));
            statement.setTimestamp(11, Timestamp.from(candidate.issuedAt()));
            if (linked) {
                statement.setNull(12, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(12, Timestamp.from(now));
            }
            return statement.executeUpdate();
        }
    }

    private static List<Lease> claimDue(
            Connection connection,
            Instant now,
            int limit,
            String owner,
            Instant leaseUntil
    ) throws SQLException {
        List<Lease> leases = selectDue(connection, now, limit, owner);
        if (leases.isEmpty()) {
            return List.of();
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE discord_outbox
                SET state='LEASED', lease_owner=?, lease_until=?, attempt_count=attempt_count+1
                WHERE message_id=? AND destination=? AND event_type=?
                """)) {
            for (Lease lease : leases) {
                update.setString(1, owner);
                update.setTimestamp(2, Timestamp.from(leaseUntil));
                update.setBytes(3, UuidBytes.toBytes(lease.messageId()));
                update.setString(4, DESTINATION);
                update.setString(5, EVENT);
                update.addBatch();
            }
            JdbcTransactionSupport.requireBatchUpdate(
                    update.executeBatch(),
                    leases.size(),
                    "Minecraft warning/mute Discord notification disappeared while acquiring lease"
            );
        }
        return leases.stream().map(Lease::incrementAttempt).toList();
    }

    private static List<Lease> selectDue(Connection connection, Instant now, int limit, String owner)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_DUE_SQL)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setTimestamp(1, timestamp);
            statement.setTimestamp(2, timestamp);
            statement.setString(3, DESTINATION);
            statement.setString(4, EVENT);
            statement.setTimestamp(5, timestamp);
            statement.setTimestamp(6, timestamp);
            statement.setInt(7, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<Lease> leases = new ArrayList<>();
                while (result.next()) {
                    Timestamp expiration = result.getTimestamp("expiration_at");
                    leases.add(new Lease(
                            UuidBytes.fromBytes(result.getBytes("message_id")),
                            owner,
                            result.getInt("attempt_count"),
                            result.getString("discord_user_id"),
                            result.getString("minecraft_name"),
                            result.getString("public_reason"),
                            result.getTimestamp("issued_at").toInstant(),
                            SanctionType.valueOf(result.getString("punishment_type")),
                            Optional.ofNullable(expiration).map(Timestamp::toInstant),
                            result.getBoolean("punishment_active")
                    ));
                }
                return leases;
            }
        }
    }

    private static Optional<String> unsignedText(BigDecimal value) {
        return value == null ? Optional.empty() : Optional.of(value.toBigIntegerExact().toString());
    }

    private static void requireInstant(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("notification time must be present");
        }
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("notification limit is outside its safe range");
        }
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("notification lease owner is invalid");
        }
    }

    private static void requireLease(Lease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("notification lease must be present");
        }
    }

    private record Candidate(
            String caseId,
            Instant issuedAt,
            SanctionType type,
            Optional<String> discordUserId
    ) {
    }

    public record Lease(
            UUID messageId,
            String owner,
            int attemptCount,
            String discordUserId,
            String minecraftName,
            String publicReason,
            Instant issuedAt,
            SanctionType type,
            Optional<Instant> expiresAt,
            boolean active
    ) {
        public Lease {
            if (messageId == null || owner == null || owner.isBlank() || attemptCount < 0
                    || discordUserId == null || discordUserId.isBlank()
                    || minecraftName == null || minecraftName.isBlank()
                    || publicReason == null || publicReason.isBlank()
                    || issuedAt == null || type == null || expiresAt == null
                    || (type != SanctionType.WARNING && type != SanctionType.MUTE)) {
                throw new IllegalArgumentException("Minecraft warning/mute Discord notification lease is invalid");
            }
        }

        private Lease incrementAttempt() {
            return new Lease(
                    messageId, owner, Math.addExact(attemptCount, 1), discordUserId,
                    minecraftName, publicReason, issuedAt, type, expiresAt, active
            );
        }
    }
}

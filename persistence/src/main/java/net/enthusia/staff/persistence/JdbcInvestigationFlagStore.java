package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.investigation.InvestigationFlag;
import net.enthusia.staff.domain.ports.InvestigationFlagStore;

public final class JdbcInvestigationFlagStore implements InvestigationFlagStore {
    private static final int ONE_UPDATED_ROW = 1;
    private final DataSource source;
    public JdbcInvestigationFlagStore(DataSource source) { this.source = java.util.Objects.requireNonNull(source, "source"); }

    @Override public void create(InvestigationFlag flag) {
        java.util.Objects.requireNonNull(flag, "flag");
        transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO player_investigation_flags
                    (flag_id,target_id,actor_id,category,reason,created_at,expires_at,case_id)
                    VALUES (?,?,?,?,?,?,?,?)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(flag.flagId()));
                statement.setBytes(2, UuidBytes.toBytes(flag.targetId()));
                statement.setBytes(3, UuidBytes.toBytes(flag.actorId()));
                statement.setString(4, flag.category());
                statement.setString(5, flag.reason());
                statement.setTimestamp(6, Timestamp.from(flag.createdAt()));
                statement.setTimestamp(7, flag.expiresAt() == null ? null : Timestamp.from(flag.expiresAt()));
                statement.setString(8, flag.caseId() == null ? null : flag.caseId().value());
                statement.executeUpdate();
            }
            audit(connection, flag.flagId(), flag.actorId(), "CREATE", flag.reason(), flag.createdAt());
            return true;
        });
    }

    @Override public List<InvestigationFlag> active(UUID targetId, Instant now, int limit) {
        java.util.Objects.requireNonNull(targetId, "targetId");
        java.util.Objects.requireNonNull(now, "now");
        if (limit < 1 || limit > 100) { throw new IllegalArgumentException("limit must be 1..100"); }
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("""
                SELECT * FROM player_investigation_flags WHERE target_id=? AND resolved_at IS NULL
                AND (expires_at IS NULL OR expires_at>?) ORDER BY created_at DESC,flag_id DESC LIMIT ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setInt(3, limit);
            try (var result = statement.executeQuery()) {
                List<InvestigationFlag> flags = new ArrayList<>();
                while (result.next()) {
                    flags.add(readFlag(result));
                }
                return List.copyOf(flags);
            }
        } catch (SQLException failure) { throw new ModerationPersistenceException("Unable to read investigation flags", failure); }
    }

    private static InvestigationFlag readFlag(java.sql.ResultSet result) throws SQLException {
        Timestamp expiry = result.getTimestamp("expires_at");
        String linkedCase = result.getString("case_id");
        return new InvestigationFlag(UuidBytes.fromBytes(result.getBytes("flag_id")),
                UuidBytes.fromBytes(result.getBytes("target_id")), UuidBytes.fromBytes(result.getBytes("actor_id")),
                result.getString("category"), result.getString("reason"), result.getTimestamp("created_at").toInstant(),
                expiry == null ? null : expiry.toInstant(), linkedCase == null ? null : new CaseId(linkedCase));
    }


    @Override public boolean resolve(UUID flagId, UUID actorId, String reason, Instant now) {
        // Reuse the domain's exact identifier/reason constraints before touching storage.
        new InvestigationFlag(flagId, flagId, actorId, "resolution", reason, now, null, null);
        return transaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE player_investigation_flags SET resolved_at=? WHERE flag_id=? AND resolved_at IS NULL")) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setBytes(2, UuidBytes.toBytes(flagId));
                if (statement.executeUpdate() != ONE_UPDATED_ROW) { return false; }
            }
            audit(connection, flagId, actorId, "RESOLVE", reason, now);
            return true;
        });
    }

    private static void audit(Connection connection, UUID flagId, UUID actorId, String action, String reason, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_investigation_flag_audit VALUES (?,?,?,?,?,?)")) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setBytes(2, UuidBytes.toBytes(flagId));
            statement.setBytes(3, UuidBytes.toBytes(actorId));
            statement.setString(4, action);
            statement.setString(5, reason);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private boolean transaction(Write write) {
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean result = write.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException failure) { throw new ModerationPersistenceException("Investigation flag transaction failed", failure); }
    }
    @FunctionalInterface private interface Write { boolean run(Connection connection) throws SQLException; }
}

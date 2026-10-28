package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException.Reason;

/** Separate read-only path; never invokes recovery or the ordinary inner-join lookup. */
final class JdbcStaffSessionInspection {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int ROW_LIMIT = 2;
    private static final int QUERY_SECONDS = 5;
    private JdbcStaffSessionInspection() {
    }

    static Optional<StaffSessionSnapshot> inspect(DataSource source, UUID staffId) {
        java.util.Objects.requireNonNull(staffId, "staffId");
        try (Connection connection = source.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            Optional<StaffSessionSnapshot> result;
            try {
                result = read(connection, staffId);
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
            connection.rollback();
            return result;
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Staff session inspection unavailable", exception);
        }
    }

    private static Optional<StaffSessionSnapshot> read(Connection connection, UUID staffId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.session_id, s.staff_id, s.server_id, s.state, s.vanish_active,
                       s.started_at, s.revision, p.session_id AS snapshot_session_id,
                       p.schema_version, p.checksum, OCTET_LENGTH(p.snapshot_blob) AS snapshot_length,
                       CASE WHEN OCTET_LENGTH(p.snapshot_blob) BETWEEN 1 AND ?
                            THEN p.snapshot_blob ELSE NULL END AS snapshot_blob
                FROM staff_sessions s LEFT JOIN staff_state_snapshots p ON p.session_id = s.session_id
                WHERE s.staff_id = ? AND s.state <> 'CLOSED'
                LIMIT 2
                """)) {
            statement.setInt(1, MAX_BYTES);
            statement.setBytes(2, UuidBytes.toBytes(staffId));
            statement.setMaxRows(ROW_LIMIT);
            statement.setQueryTimeout(QUERY_SECONDS);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                StaffSessionSnapshot snapshot = validated(rows, staffId);
                if (rows.next()) {
                    throw new StaffSessionInspectionException(Reason.AMBIGUOUS_SESSION);
                }
                return Optional.of(snapshot);
            }
        }
    }

    private static StaffSessionSnapshot validated(ResultSet row, UUID staffId) throws SQLException {
        byte[] snapshotSession = row.getBytes("snapshot_session_id");
        if (snapshotSession == null) {
            throw new StaffSessionInspectionException(Reason.MISSING_SNAPSHOT);
        }
        if (!Arrays.equals(snapshotSession, row.getBytes("session_id"))) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
        validateBytes(row);
        try {
            StaffSessionSnapshot snapshot = JdbcStaffSessionStore.read(row);
            if (!staffId.equals(snapshot.staffId())) {
                throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
            }
            return snapshot;
        } catch (IllegalArgumentException exception) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
    }

    private static void validateBytes(ResultSet row) throws SQLException {
        long length = row.getLong("snapshot_length");
        if (length < 1 || length > MAX_BYTES) {
            throw new StaffSessionInspectionException(Reason.INVALID_SNAPSHOT_LENGTH);
        }
        byte[] bytes = row.getBytes("snapshot_blob");
        if (bytes == null || bytes.length != length) {
            throw new StaffSessionInspectionException(Reason.INVALID_SNAPSHOT_LENGTH);
        }
        try {
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!checksum.equals(row.getString("checksum"))) {
                throw new StaffSessionInspectionException(Reason.CHECKSUM_MISMATCH);
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

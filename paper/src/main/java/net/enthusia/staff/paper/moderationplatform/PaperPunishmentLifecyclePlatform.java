package net.enthusia.staff.paper.moderationplatform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import net.enthusia.staff.moderation.api.PunishmentCategory;
import net.enthusia.staff.moderation.api.PunishmentLifecycleCursor;
import net.enthusia.staff.moderation.api.PunishmentLifecycleEvent;
import net.enthusia.staff.moderation.api.PunishmentLifecyclePage;
import net.enthusia.staff.moderation.api.PunishmentLifecyclePlatform;
import net.enthusia.staff.moderation.api.PunishmentLifecycleSource;
import net.enthusia.staff.persistence.UuidBytes;

public final class PaperPunishmentLifecyclePlatform implements PunishmentLifecyclePlatform {
    private static final List<String> TERMINAL_STATUSES = List.of(
            "EXPIRED", "ENDED_EARLY", "REVOKED", "OVERTURNED"
    );

    private final Supplier<DataSource> dataSource;
    private final ExecutorService workers;
    private final Clock clock;

    public PaperPunishmentLifecyclePlatform(
            Supplier<DataSource> dataSource,
            ExecutorService workers,
            Clock clock
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public int apiVersion() {
        return PunishmentLifecyclePlatform.API_VERSION;
    }

    @Override
    public boolean available() {
        return dataSource.get() != null;
    }

    @Override
    public CompletionStage<PunishmentLifecyclePage> readAfter(
            PunishmentLifecycleCursor cursor,
            int limit
    ) {
        Objects.requireNonNull(cursor, "cursor");
        if (limit < 1 || limit > PunishmentLifecyclePlatform.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + PunishmentLifecyclePlatform.MAX_PAGE_SIZE
            );
        }
        try {
            return CompletableFuture.supplyAsync(() -> load(cursor, limit), workers);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private PunishmentLifecyclePage load(PunishmentLifecycleCursor cursor, int limit) {
        DataSource current = dataSource.get();
        if (current == null) {
            throw new IllegalStateException("moderation persistence is unavailable");
        }
        String sql = """
                SELECT sanction.sanction_id, sanction.sanction_type, sanction.status,
                       sanction.issued_at, sanction.expiration_at,
                       moderation_case.case_id, moderation_case.target_id,
                       moderation_case.public_reason, moderation_case.actor_name,
                       player.current_username,
                       migration.external_id
                FROM sanctions sanction
                JOIN cases moderation_case ON moderation_case.case_id = sanction.case_id
                LEFT JOIN players player ON player.player_id = sanction.target_id
                LEFT JOIN migration_mappings migration
                    ON migration.case_id = sanction.case_id
                   AND migration.source_system = 'LITEBANS'
                WHERE sanction.sanction_id > ?
                  AND sanction.inherited_from IS NULL
                ORDER BY sanction.sanction_id ASC
                LIMIT ?
                """;
        List<PunishmentLifecycleEvent> events = new ArrayList<>();
        try (Connection connection = current.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(cursor.sanctionId()));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    events.add(map(rows, clock.instant()));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read punishment lifecycle snapshot", exception);
        }
        PunishmentLifecycleCursor next = events.isEmpty()
                ? cursor
                : new PunishmentLifecycleCursor(events.getLast().sanctionId());
        return new PunishmentLifecyclePage(events, next, events.size() == limit);
    }

    private static PunishmentLifecycleEvent map(ResultSet row, Instant now) throws SQLException {
        UUID sanctionId = UuidBytes.fromBytes(row.getBytes("sanction_id"));
        PunishmentCategory category = category(row.getString("sanction_type"));
        Optional<Instant> expiration = optionalInstant(row.getTimestamp("expiration_at"));
        String externalId = row.getString("external_id");
        PunishmentLifecycleSource source = externalId == null
                ? PunishmentLifecycleSource.ENTHUSIA_STAFF
                : PunishmentLifecycleSource.LITEBANS;
        String sourcePunishmentId = externalId == null ? sanctionId.toString() : externalId;
        return new PunishmentLifecycleEvent(
                sanctionId,
                row.getString("case_id"),
                UuidBytes.fromBytes(row.getBytes("target_id")),
                optionalText(row.getString("current_username")),
                category,
                source,
                sourcePunishmentId,
                row.getTimestamp("issued_at").toInstant(),
                expiration,
                Objects.requireNonNullElse(row.getString("public_reason"), ""),
                optionalText(row.getString("actor_name")),
                active(category, row.getString("status"), expiration, now)
        );
    }

    static PunishmentCategory category(String sanctionType) {
        return switch (Objects.requireNonNull(sanctionType, "sanctionType").toUpperCase(Locale.ROOT)) {
            case "WARNING" -> PunishmentCategory.WARN;
            case "KICK" -> PunishmentCategory.KICK;
            case "MUTE", "PUBLIC_MUTE" -> PunishmentCategory.MUTE;
            case "BAN", "NETWORK_BAN", "NETWORK_IDENTITY_BAN" -> PunishmentCategory.BAN;
            default -> PunishmentCategory.OTHER;
        };
    }

    static boolean active(
            PunishmentCategory category,
            String status,
            Optional<Instant> expiration,
            Instant now
    ) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiration, "expiration");
        Objects.requireNonNull(now, "now");
        if (TERMINAL_STATUSES.contains(status.toUpperCase(Locale.ROOT))) {
            return false;
        }
        if ((category == PunishmentCategory.MUTE || category == PunishmentCategory.BAN)
                && expiration.isPresent()
                && !expiration.orElseThrow().isAfter(now)) {
            return false;
        }
        return true;
    }

    private static Optional<Instant> optionalInstant(Timestamp value) {
        return Optional.ofNullable(value).map(Timestamp::toInstant);
    }

    private static Optional<String> optionalText(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}

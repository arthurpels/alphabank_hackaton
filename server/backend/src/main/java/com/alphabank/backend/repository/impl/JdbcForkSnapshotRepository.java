package com.alphabank.backend.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;

@Repository
public class JdbcForkSnapshotRepository implements ForkSnapshotRepository {

    private static final String COLUMNS = """
            SELECT id, application_id, rate_from, rate_to, approve_max, reject_min, rules_version, as_of, calculated_at
            FROM fork_snapshot
            WHERE application_id = :applicationId
              AND id NOT IN (SELECT fork_snapshot_id FROM offer WHERE application_id = :applicationId)
            ORDER BY calculated_at, id
            """;

    private final JdbcClient jdbc;

    public JdbcForkSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ForkSnapshotRecord snapshot) {
        jdbc.sql("""
                        INSERT INTO fork_snapshot (id, application_id, rate_from, rate_to, approve_max, reject_min,
                                                   rules_version, as_of, calculated_at)
                        VALUES (:id, :applicationId, :rateFrom, :rateTo, :approveMax, :rejectMin,
                                :rulesVersion, :asOf, :calculatedAt)
                        """)
                .param("id", snapshot.id())
                .param("applicationId", snapshot.applicationId())
                .param("rateFrom", snapshot.rateFrom())
                .param("rateTo", snapshot.rateTo())
                .param("approveMax", snapshot.approveMax())
                .param("rejectMin", snapshot.rejectMin())
                .param("rulesVersion", snapshot.rulesVersion())
                .param("asOf", snapshot.asOf())
                .param("calculatedAt", snapshot.calculatedAt())
                .update();
    }

    @Override
    public Optional<ForkSnapshotRecord> findLatest(UUID applicationId) {
        List<ForkSnapshotRecord> history = findByApplicationId(applicationId);
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1));
    }

    @Override
    public List<ForkSnapshotRecord> findByApplicationId(UUID applicationId) {
        return jdbc.sql(COLUMNS)
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    public Optional<ForkSnapshotRecord> findById(UUID id) {
        return jdbc.sql("""
                        SELECT id, application_id, rate_from, rate_to, approve_max, reject_min, rules_version, as_of,
                               calculated_at
                        FROM fork_snapshot
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    private static ForkSnapshotRecord map(ResultSet rs) throws SQLException {
        double approveMax = rs.getDouble("approve_max");
        boolean approveNull = rs.wasNull();
        double rejectMin = rs.getDouble("reject_min");
        boolean rejectNull = rs.wasNull();
        return new ForkSnapshotRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("application_id", UUID.class),
                rs.getDouble("rate_from"),
                rs.getDouble("rate_to"),
                approveNull ? null : approveMax,
                rejectNull ? null : rejectMin,
                rs.getString("rules_version"),
                rs.getObject("as_of", LocalDate.class),
                rs.getObject("calculated_at", OffsetDateTime.class));
    }
}

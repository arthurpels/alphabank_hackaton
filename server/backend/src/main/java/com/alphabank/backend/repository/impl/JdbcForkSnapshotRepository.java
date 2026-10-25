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

import com.alphabank.backend.model.SnapshotTrace;
import com.alphabank.backend.model.UsedVerdict;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcForkSnapshotRepository implements ForkSnapshotRepository {

    private static final String SELECT = """
            SELECT id, application_id, rate_from, rate_to, approve_max, reject_min, rules_version, as_of, calculated_at,
                   score_request, request_hash, score_response, engine_version, verdicts::text AS verdicts
            FROM fork_snapshot
            """;
    private static final String HISTORY = SELECT + """
            WHERE application_id = :applicationId
              AND id NOT IN (SELECT fork_snapshot_id FROM offer WHERE application_id = :applicationId)
            ORDER BY calculated_at, id
            """;
    private static final TypeReference<List<UsedVerdict>> VERDICTS_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcForkSnapshotRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(ForkSnapshotRecord snapshot) {
        SnapshotTrace trace = snapshot.trace();
        jdbc.sql("""
                        INSERT INTO fork_snapshot (id, application_id, rate_from, rate_to, approve_max, reject_min,
                                                   rules_version, as_of, calculated_at, score_request, request_hash,
                                                   score_response, engine_version, verdicts)
                        VALUES (:id, :applicationId, :rateFrom, :rateTo, :approveMax, :rejectMin,
                                :rulesVersion, :asOf, :calculatedAt, :scoreRequest, :requestHash,
                                :scoreResponse, :engineVersion, CAST(:verdicts AS jsonb))
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
                .param("scoreRequest", trace == null ? null : trace.scoreRequest())
                .param("requestHash", trace == null ? null : trace.requestHash())
                .param("scoreResponse", trace == null ? null : trace.scoreResponse())
                .param("engineVersion", trace == null ? null : trace.engineVersion())
                .param("verdicts", trace == null ? null : toJson(trace.verdicts()))
                .update();
    }

    @Override
    public Optional<ForkSnapshotRecord> findLatest(UUID applicationId) {
        List<ForkSnapshotRecord> history = findByApplicationId(applicationId);
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1));
    }

    @Override
    public List<ForkSnapshotRecord> findByApplicationId(UUID applicationId) {
        return jdbc.sql(HISTORY)
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    public Optional<ForkSnapshotRecord> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    private ForkSnapshotRecord map(ResultSet rs) throws SQLException {
        double approveMax = rs.getDouble("approve_max");
        boolean approveNull = rs.wasNull();
        double rejectMin = rs.getDouble("reject_min");
        boolean rejectNull = rs.wasNull();
        String scoreRequest = rs.getString("score_request");
        SnapshotTrace trace = scoreRequest == null ? null : new SnapshotTrace(scoreRequest,
                rs.getString("request_hash"), rs.getString("score_response"), rs.getString("engine_version"),
                fromJson(rs.getString("verdicts")));
        return new ForkSnapshotRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("application_id", UUID.class),
                rs.getDouble("rate_from"),
                rs.getDouble("rate_to"),
                approveNull ? null : approveMax,
                rejectNull ? null : rejectMin,
                rs.getString("rules_version"),
                rs.getObject("as_of", LocalDate.class),
                rs.getObject("calculated_at", OffsetDateTime.class),
                trace);
    }

    private String toJson(List<UsedVerdict> verdicts) {
        try {
            return objectMapper.writeValueAsString(verdicts == null ? List.of() : verdicts);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сохранить вердикты снимка", e);
        }
    }

    private List<UsedVerdict> fromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, VERDICTS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректные вердикты снимка в БД", e);
        }
    }
}

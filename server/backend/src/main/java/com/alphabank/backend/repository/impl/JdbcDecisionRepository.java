package com.alphabank.backend.repository.impl;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.DecisionType;
import com.alphabank.backend.repository.DecisionRepository;
import com.alphabank.backend.repository.model.DecisionRecord;

@Repository
public class JdbcDecisionRepository implements DecisionRepository {

    private final JdbcClient jdbc;

    public JdbcDecisionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(DecisionRecord decision) {
        jdbc.sql("""
                        INSERT INTO decision (id, application_id, type, offer_id, decided_by, decided_by_user_id,
                                              rules_version, override, comment, reason_spot_id, decided_at,
                                              fork_snapshot_id, request_hash)
                        VALUES (:id, :applicationId, :type, :offerId, :decidedBy, :decidedByUserId,
                                :rulesVersion, :override, :comment, :reasonSpotId, :decidedAt,
                                :forkSnapshotId, :requestHash)
                        """)
                .param("id", decision.id())
                .param("applicationId", decision.applicationId())
                .param("type", decision.type().name())
                .param("offerId", decision.offerId())
                .param("decidedBy", decision.decidedBy().name())
                .param("decidedByUserId", decision.decidedByUserId())
                .param("rulesVersion", decision.rulesVersion())
                .param("override", decision.override())
                .param("comment", decision.comment())
                .param("reasonSpotId", decision.reasonSpotId())
                .param("decidedAt", decision.decidedAt())
                .param("forkSnapshotId", decision.forkSnapshotId())
                .param("requestHash", decision.requestHash())
                .update();
    }

    @Override
    public void setOffer(UUID applicationId, UUID offerId) {
        jdbc.sql("UPDATE decision SET offer_id = :offerId WHERE application_id = :applicationId")
                .param("offerId", offerId)
                .param("applicationId", applicationId)
                .update();
    }

    @Override
    public Optional<DecisionRecord> findByApplicationId(UUID applicationId) {
        return jdbc.sql("""
                        SELECT id, application_id, type, offer_id, decided_by, decided_by_user_id, rules_version,
                               override, comment, reason_spot_id, decided_at, fork_snapshot_id, request_hash
                        FROM decision
                        WHERE application_id = :applicationId
                        """)
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> new DecisionRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class),
                        DecisionType.valueOf(rs.getString("type")),
                        rs.getObject("offer_id", UUID.class),
                        DecidedBy.valueOf(rs.getString("decided_by")),
                        rs.getObject("decided_by_user_id", UUID.class),
                        rs.getString("rules_version"),
                        rs.getBoolean("override"),
                        rs.getString("comment"),
                        rs.getObject("reason_spot_id", UUID.class),
                        rs.getObject("decided_at", OffsetDateTime.class),
                        rs.getObject("fork_snapshot_id", UUID.class),
                        rs.getString("request_hash")))
                .optional();
    }
}

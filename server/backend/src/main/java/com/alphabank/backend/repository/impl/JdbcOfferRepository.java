package com.alphabank.backend.repository.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.model.OfferRecord;

@Repository
public class JdbcOfferRepository implements OfferRepository {

    private final JdbcClient jdbc;

    public JdbcOfferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int expireActive(UUID applicationId) {
        return jdbc.sql("""
                        UPDATE offer SET status = 'EXPIRED'
                        WHERE application_id = :applicationId AND status IN ('CALCULATED', 'OFFERED')
                        """)
                .param("applicationId", applicationId)
                .update();
    }

    @Override
    public void insert(OfferRecord offer) {
        jdbc.sql("""
                        INSERT INTO offer (id, application_id, product_id, amount, term_months, collateral_type,
                                           collateral_comment, fork_snapshot_id, proposed_by, status, comment,
                                           created_at, chosen_at)
                        VALUES (:id, :applicationId, :productId, :amount, :termMonths, :collateralType,
                                :collateralComment, :forkSnapshotId, :proposedBy, :status, :comment,
                                :createdAt, :chosenAt)
                        """)
                .param("id", offer.id())
                .param("applicationId", offer.applicationId())
                .param("productId", offer.productId())
                .param("amount", offer.amount())
                .param("termMonths", offer.termMonths())
                .param("collateralType", offer.collateralType())
                .param("collateralComment", offer.collateralComment())
                .param("forkSnapshotId", offer.forkSnapshotId())
                .param("proposedBy", offer.proposedBy().name())
                .param("status", offer.status().name())
                .param("comment", offer.comment())
                .param("createdAt", offer.createdAt())
                .param("chosenAt", offer.chosenAt())
                .update();
    }

    @Override
    public void updateStatus(UUID id, OfferStatus status) {
        jdbc.sql("UPDATE offer SET status = :status WHERE id = :id")
                .param("status", status.name())
                .param("id", id)
                .update();
    }

    @Override
    public List<OfferRecord> findByApplicationId(UUID applicationId) {
        return jdbc.sql("""
                        SELECT id, application_id, product_id, amount, term_months, collateral_type,
                               collateral_comment, fork_snapshot_id, proposed_by, status, comment, created_at, chosen_at
                        FROM offer
                        WHERE application_id = :applicationId
                        ORDER BY created_at, id
                        """)
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> new OfferRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class),
                        rs.getObject("product_id", UUID.class),
                        rs.getLong("amount"),
                        rs.getInt("term_months"),
                        rs.getString("collateral_type"),
                        rs.getString("collateral_comment"),
                        rs.getObject("fork_snapshot_id", UUID.class),
                        DecidedBy.valueOf(rs.getString("proposed_by")),
                        OfferStatus.valueOf(rs.getString("status")),
                        rs.getString("comment"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("chosen_at", OffsetDateTime.class)))
                .list();
    }
}

package com.alphabank.backend.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;

@Repository
public class JdbcSpotAnswerRepository implements SpotAnswerRepository {

    private final JdbcClient jdbc;

    public JdbcSpotAnswerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SpotAnswerRecord answer) {
        jdbc.sql("""
                        INSERT INTO spot_answer (id, spot_id, option_code, text, document_id, contract_amount,
                                                 contract_term_months, verdict, reviewed_by, review_comment,
                                                 created_at, reviewed_at)
                        VALUES (:id, :spotId, :optionCode, :text, :documentId, :contractAmount,
                                :contractTermMonths, :verdict, :reviewedBy, :reviewComment, :createdAt, :reviewedAt)
                        """)
                .param("id", answer.id())
                .param("spotId", answer.spotId())
                .param("optionCode", answer.optionCode())
                .param("text", answer.text())
                .param("documentId", answer.documentId())
                .param("contractAmount", answer.contractAmount())
                .param("contractTermMonths", answer.contractTermMonths())
                .param("verdict", answer.verdict().name())
                .param("reviewedBy", answer.reviewedBy())
                .param("reviewComment", answer.reviewComment())
                .param("createdAt", answer.createdAt())
                .param("reviewedAt", answer.reviewedAt())
                .update();
    }

    @Override
    public Map<UUID, SpotAnswerRecord> findLatestBySpotIds(List<UUID> spotIds) {
        if (spotIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT DISTINCT ON (spot_id) id, spot_id, option_code, text, document_id, contract_amount,
                               contract_term_months, verdict, reviewed_by, review_comment, created_at, reviewed_at
                        FROM spot_answer
                        WHERE spot_id IN (:spotIds)
                        ORDER BY spot_id, created_at DESC, id
                        """)
                .param("spotIds", spotIds)
                .query(JdbcSpotAnswerRepository::map)
                .list()
                .stream()
                .collect(Collectors.toMap(SpotAnswerRecord::spotId, Function.identity()));
    }

    @Override
    public List<SpotAnswerRecord> findAcceptedContracts(UUID applicationId) {
        return jdbc.sql("""
                        SELECT a.id, a.spot_id, a.option_code, a.text, a.document_id, a.contract_amount,
                               a.contract_term_months, a.verdict, a.reviewed_by, a.review_comment, a.created_at,
                               a.reviewed_at
                        FROM spot_answer a
                        JOIN spot s ON s.id = a.spot_id
                        WHERE s.application_id = :applicationId AND s.status = 'CLOSED'
                          AND a.verdict = 'ACCEPTED' AND a.contract_amount IS NOT NULL
                        ORDER BY a.created_at, a.id
                        """)
                .param("applicationId", applicationId)
                .query(JdbcSpotAnswerRepository::map)
                .list();
    }

    @Override
    public void review(UUID id, AnswerVerdict verdict, String reviewedBy, String reviewComment,
                       OffsetDateTime reviewedAt) {
        jdbc.sql("""
                        UPDATE spot_answer
                        SET verdict = :verdict, reviewed_by = :reviewedBy, review_comment = :reviewComment,
                            reviewed_at = :reviewedAt
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("verdict", verdict.name())
                .param("reviewedBy", reviewedBy)
                .param("reviewComment", reviewComment)
                .param("reviewedAt", reviewedAt)
                .update();
    }

    private static SpotAnswerRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new SpotAnswerRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("spot_id", UUID.class),
                rs.getString("option_code"),
                rs.getString("text"),
                rs.getObject("document_id", UUID.class),
                rs.getObject("contract_amount", Long.class),
                rs.getObject("contract_term_months", Integer.class),
                AnswerVerdict.valueOf(rs.getString("verdict")),
                rs.getString("reviewed_by"),
                rs.getString("review_comment"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("reviewed_at", OffsetDateTime.class));
    }
}

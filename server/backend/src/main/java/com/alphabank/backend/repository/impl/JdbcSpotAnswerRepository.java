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

import com.alphabank.backend.dto.response.PurposeVerdictDto;
import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcSpotAnswerRepository implements SpotAnswerRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSpotAnswerRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(SpotAnswerRecord answer) {
        jdbc.sql("""
                        INSERT INTO spot_answer (id, spot_id, option_code, text, document_id, contract_amount,
                                                 contract_term_months, verdict, reviewed_by, review_comment,
                                                 created_at, reviewed_at, purpose_verdict)
                        VALUES (:id, :spotId, :optionCode, :text, :documentId, :contractAmount,
                                :contractTermMonths, :verdict, :reviewedBy, :reviewComment, :createdAt, :reviewedAt,
                                CAST(:purposeVerdict AS jsonb))
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
                .param("purposeVerdict", toJson(answer.purposeVerdict()))
                .update();
    }

    @Override
    public Map<UUID, SpotAnswerRecord> findLatestBySpotIds(List<UUID> spotIds) {
        if (spotIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT DISTINCT ON (spot_id) id, spot_id, option_code, text, document_id, contract_amount,
                               contract_term_months, verdict, reviewed_by, review_comment, created_at, reviewed_at,
                               purpose_verdict::text AS purpose_verdict
                        FROM spot_answer
                        WHERE spot_id IN (:spotIds)
                        ORDER BY spot_id, created_at DESC, id
                        """)
                .param("spotIds", spotIds)
                .query(this::map)
                .list()
                .stream()
                .collect(Collectors.toMap(SpotAnswerRecord::spotId, Function.identity()));
    }

    @Override
    public List<SpotAnswerRecord> findAcceptedContracts(UUID applicationId) {
        return jdbc.sql("""
                        SELECT a.id, a.spot_id, a.option_code, a.text, a.document_id, a.contract_amount,
                               a.contract_term_months, a.verdict, a.reviewed_by, a.review_comment, a.created_at,
                               a.reviewed_at, a.purpose_verdict::text AS purpose_verdict
                        FROM spot_answer a
                        JOIN spot s ON s.id = a.spot_id
                        WHERE s.application_id = :applicationId AND s.status = 'CLOSED'
                          AND a.verdict = 'ACCEPTED' AND a.contract_amount IS NOT NULL
                        ORDER BY a.created_at, a.id
                        """)
                .param("applicationId", applicationId)
                .query(this::map)
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

    @Override
    public List<SpotAnswerRecord> findBySpotId(UUID spotId) {
        return jdbc.sql("""
                        SELECT id, spot_id, option_code, text, document_id, contract_amount, contract_term_months,
                               verdict, reviewed_by, review_comment, created_at, reviewed_at,
                               purpose_verdict::text AS purpose_verdict
                        FROM spot_answer
                        WHERE spot_id = :spotId
                        ORDER BY created_at, id
                        """)
                .param("spotId", spotId)
                .query(this::map)
                .list();
    }

    private SpotAnswerRecord map(ResultSet rs, int rowNum) throws SQLException {
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
                rs.getObject("reviewed_at", OffsetDateTime.class),
                fromJson(rs.getString("purpose_verdict")));
    }

    private String toJson(PurposeVerdictDto verdict) {
        if (verdict == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(verdict);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сохранить вердикт по цели", e);
        }
    }

    private PurposeVerdictDto fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PurposeVerdictDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректный вердикт по цели в БД", e);
        }
    }
}

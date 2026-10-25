package com.alphabank.backend.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.dto.response.LlmMetaDto;
import com.alphabank.backend.dto.response.SummaryItemDto;
import com.alphabank.backend.enums.SummaryStatus;
import com.alphabank.backend.repository.UnderwritingSummaryRepository;
import com.alphabank.backend.repository.model.UnderwritingSummaryRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcUnderwritingSummaryRepository implements UnderwritingSummaryRepository {

    private static final String COLUMNS = """
            SELECT application_id, status, generated_by, items, llm, generated_at, updated_at, revision
            FROM underwriting_summary
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcUnderwritingSummaryRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<UnderwritingSummaryRecord> findByApplicationId(UUID applicationId) {
        return jdbc.sql(COLUMNS + "WHERE application_id = :applicationId")
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    @Override
    public long saveTemplate(UUID applicationId, SummaryStatus status, List<SummaryItemDto> items, OffsetDateTime now) {
        return jdbc.sql("""
                        INSERT INTO underwriting_summary (application_id, status, generated_by, items, llm,
                                                          generated_at, updated_at, revision)
                        VALUES (:applicationId, :status, 'TEMPLATE', CAST(:items AS jsonb), NULL, :now, :now, 1)
                        ON CONFLICT (application_id) DO UPDATE SET
                            status = EXCLUDED.status,
                            generated_by = 'TEMPLATE',
                            items = EXCLUDED.items,
                            llm = NULL,
                            generated_at = EXCLUDED.generated_at,
                            updated_at = EXCLUDED.updated_at,
                            revision = underwriting_summary.revision + 1
                        RETURNING revision
                        """)
                .param("applicationId", applicationId)
                .param("status", status.value())
                .param("items", write(items))
                .param("now", now)
                .query(Long.class)
                .single();
    }

    @Override
    public void complete(UUID applicationId, long revision, String generatedBy, List<SummaryItemDto> items,
                         LlmMetaDto llm, OffsetDateTime now) {
        jdbc.sql("""
                        UPDATE underwriting_summary
                        SET status = 'ready', generated_by = :generatedBy, items = CAST(:items AS jsonb),
                            llm = CAST(:llm AS jsonb), generated_at = :now, updated_at = :now
                        WHERE application_id = :applicationId AND revision = :revision
                        """)
                .param("applicationId", applicationId)
                .param("revision", revision)
                .param("generatedBy", generatedBy)
                .param("items", write(items))
                .param("llm", llm == null ? null : write(llm))
                .param("now", now)
                .update();
    }

    @Override
    public void markReady(UUID applicationId, long revision, OffsetDateTime now) {
        jdbc.sql("""
                        UPDATE underwriting_summary
                        SET status = 'ready', updated_at = :now
                        WHERE application_id = :applicationId AND revision = :revision
                        """)
                .param("applicationId", applicationId)
                .param("revision", revision)
                .param("now", now)
                .update();
    }

    private UnderwritingSummaryRecord map(ResultSet rs) throws SQLException {
        try {
            String llm = rs.getString("llm");
            return new UnderwritingSummaryRecord(
                    rs.getObject("application_id", UUID.class),
                    SummaryStatus.valueOf(rs.getString("status").toUpperCase()),
                    rs.getString("generated_by"),
                    objectMapper.readValue(rs.getString("items"),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, SummaryItemDto.class)),
                    llm == null ? null : objectMapper.readValue(llm, LlmMetaDto.class),
                    rs.getObject("generated_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getLong("revision"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректная сохранённая сводка", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить сводку", e);
        }
    }
}

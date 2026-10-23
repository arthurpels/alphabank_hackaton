package com.alphabank.backend.repository.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditLogRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(UUID applicationId, AuditEventDto event) {
        jdbc.sql("""
                        INSERT INTO audit_log (id, application_id, user_id, event, from_state, to_state,
                                               payload, reason, created_at)
                        VALUES (:id, :applicationId, :userId, :event, :fromState, :toState,
                                CAST(:payload AS jsonb), :reason, :createdAt)
                        """)
                .param("id", event.id())
                .param("applicationId", applicationId)
                .param("userId", event.userId())
                .param("event", event.event().name())
                .param("fromState", event.fromState())
                .param("toState", event.toState())
                .param("payload", toJson(event.payload()))
                .param("reason", event.reason())
                .param("createdAt", event.createdAt())
                .update();
    }

    @Override
    public List<AuditEventDto> findByApplicationId(UUID applicationId) {
        return jdbc.sql("""
                        SELECT id, user_id, event, from_state, to_state, payload::text AS payload, reason, created_at
                        FROM audit_log
                        WHERE application_id = :applicationId
                        ORDER BY created_at, id
                        """)
                .param("applicationId", applicationId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    private AuditEventDto map(ResultSet rs) throws SQLException {
        return new AuditEventDto(
                rs.getObject("id", UUID.class),
                AuditEventType.valueOf(rs.getString("event")),
                rs.getString("from_state"),
                rs.getString("to_state"),
                rs.getObject("user_id", UUID.class),
                rs.getString("reason"),
                fromJson(rs.getString("payload")),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать payload журнала", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Некорректный payload в журнале", e);
        }
    }
}

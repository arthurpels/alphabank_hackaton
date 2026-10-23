package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.alphabank.backend.enums.AuditEventType;

public record AuditEventDto(
        UUID id,
        AuditEventType event,
        String fromState,
        String toState,
        UUID userId,
        String reason,
        Map<String, Object> payload,
        OffsetDateTime createdAt) {
}

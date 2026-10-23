package com.alphabank.backend.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;

public interface AuditService {

    AuditEventDto recordStatusChange(UUID applicationId, ApplicationStatus from, ApplicationStatus to,
                                     UUID userId, String reason);

    AuditEventDto record(UUID applicationId, AuditEventType event, UUID userId, String reason,
                         Map<String, Object> payload);

    List<AuditEventDto> getHistory(UUID applicationId);
}

package com.alphabank.backend.repository;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.AuditEventDto;

public interface AuditLogRepository {

    void save(UUID applicationId, AuditEventDto event);

    List<AuditEventDto> findByApplicationId(UUID applicationId);
}

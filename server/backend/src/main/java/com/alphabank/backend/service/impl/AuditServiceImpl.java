package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.repository.AuditLogRepository;
import com.alphabank.backend.service.AuditService;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditServiceImpl(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public AuditEventDto recordStatusChange(UUID applicationId, ApplicationStatus from, ApplicationStatus to,
                                            UUID userId, String reason) {
        return save(applicationId, new AuditEventDto(UUID.randomUUID(), AuditEventType.STATUS_CHANGE,
                from == null ? null : from.name(), to.name(), userId, reason, null, OffsetDateTime.now(clock)));
    }

    @Override
    public AuditEventDto record(UUID applicationId, AuditEventType event, UUID userId, String reason,
                                Map<String, Object> payload) {
        return save(applicationId, new AuditEventDto(UUID.randomUUID(), event, null, null, userId, reason,
                payload, OffsetDateTime.now(clock)));
    }

    @Override
    public List<AuditEventDto> getHistory(UUID applicationId) {
        return repository.findByApplicationId(applicationId);
    }

    private AuditEventDto save(UUID applicationId, AuditEventDto event) {
        repository.save(applicationId, event);
        return event;
    }
}

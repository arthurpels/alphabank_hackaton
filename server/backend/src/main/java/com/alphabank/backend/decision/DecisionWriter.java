package com.alphabank.backend.decision;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.DecisionType;
import com.alphabank.backend.repository.DecisionRepository;
import com.alphabank.backend.repository.model.DecisionRecord;
import com.alphabank.backend.service.AuditService;

@Component
public class DecisionWriter {

    private final DecisionRepository decisionRepository;
    private final AuditService auditService;
    private final Clock clock;

    public DecisionWriter(DecisionRepository decisionRepository, AuditService auditService, Clock clock) {
        this.decisionRepository = decisionRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    public void bySystem(UUID applicationId, DecisionType type, String rulesVersion, UUID reasonSpotId,
                         String comment) {
        decisionRepository.insert(new DecisionRecord(UUID.randomUUID(), applicationId, type, null, DecidedBy.SYSTEM,
                null, rulesVersion, false, comment, reasonSpotId, OffsetDateTime.now(clock)));
        auditService.record(applicationId, AuditEventType.DECISION, null, comment,
                Map.of("type", type.name(), "decidedBy", DecidedBy.SYSTEM.name()));
    }

    public void byUnderwriter(UUID applicationId, DecisionType type, UUID userId, String rulesVersion,
                              boolean override, UUID reasonSpotId, String comment, List<UUID> offerIds) {
        decisionRepository.insert(new DecisionRecord(UUID.randomUUID(), applicationId, type, null,
                DecidedBy.UNDERWRITER, userId, rulesVersion, override, comment, reasonSpotId,
                OffsetDateTime.now(clock)));
        auditService.record(applicationId, AuditEventType.DECISION, userId, comment,
                Map.of("type", type.name(), "decidedBy", DecidedBy.UNDERWRITER.name(),
                        "offerIds", offerIds.stream().map(UUID::toString).toList()));
    }
}

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
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
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

    public void bySystem(UUID applicationId, DecisionType type, ForkSnapshotRecord snapshot, UUID reasonSpotId,
                         String comment) {
        decisionRepository.insert(record(applicationId, type, DecidedBy.SYSTEM, null, snapshot, false, comment,
                reasonSpotId));
        auditService.record(applicationId, AuditEventType.DECISION, null, comment,
                Map.of("type", type.name(), "decidedBy", DecidedBy.SYSTEM.name()));
    }

    public void byUnderwriter(UUID applicationId, DecisionType type, UUID userId, ForkSnapshotRecord snapshot,
                              boolean override, UUID reasonSpotId, String comment, List<UUID> offerIds) {
        decisionRepository.insert(record(applicationId, type, DecidedBy.UNDERWRITER, userId, snapshot, override,
                comment, reasonSpotId));
        auditService.record(applicationId, AuditEventType.DECISION, userId, comment,
                Map.of("type", type.name(), "decidedBy", DecidedBy.UNDERWRITER.name(),
                        "offerIds", offerIds.stream().map(UUID::toString).toList()));
    }

    private DecisionRecord record(UUID applicationId, DecisionType type, DecidedBy decidedBy, UUID userId,
                                  ForkSnapshotRecord snapshot, boolean override, String comment,
                                  UUID reasonSpotId) {
        return new DecisionRecord(UUID.randomUUID(), applicationId, type, null, decidedBy, userId,
                snapshot == null ? null : snapshot.rulesVersion(), override, comment, reasonSpotId,
                OffsetDateTime.now(clock), snapshot == null ? null : snapshot.id(),
                snapshot == null || snapshot.trace() == null ? null : snapshot.trace().requestHash());
    }
}

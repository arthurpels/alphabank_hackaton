package com.alphabank.backend.decision;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.OfferRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.service.AuditService;

@Component
public class OfferWriter {

    private final ForkSnapshotRepository forkSnapshotRepository;
    private final OfferRepository offerRepository;
    private final AuditService auditService;

    public OfferWriter(ForkSnapshotRepository forkSnapshotRepository, OfferRepository offerRepository,
                       AuditService auditService) {
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.offerRepository = offerRepository;
        this.auditService = auditService;
    }

    public OfferRecord save(UUID applicationId, ProductRecord product, long amount, int termMonths,
                            String collateralType, ScoreResponse fork, OfferStatus status, OffsetDateTime createdAt) {
        ForkSnapshotRecord snapshot = new ForkSnapshotRecord(UUID.randomUUID(), applicationId, fork.rateFrom(),
                fork.rateTo(), fork.thresholds().approveMax(), fork.thresholds().rejectMin(), fork.rulesVersion(),
                createdAt);
        forkSnapshotRepository.insert(snapshot);
        OfferRecord offer = new OfferRecord(UUID.randomUUID(), applicationId, product.id(), amount, termMonths,
                collateralType, null, snapshot.id(), DecidedBy.SYSTEM, status, null, createdAt, null);
        offerRepository.insert(offer);
        auditService.record(applicationId, AuditEventType.OFFER_CREATED, null,
                product.code() + ", " + amount / 100 + " ₽, " + termMonths + " мес.",
                Map.of("offerId", offer.id().toString(), "status", status.name()));
        return offer;
    }
}

package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.decision.AutoDecision;
import com.alphabank.backend.decision.AutoDecisionRule;
import com.alphabank.backend.decision.DecisionWriter;
import com.alphabank.backend.decision.GridOfferWriter;
import com.alphabank.backend.decision.OfferWriter;
import com.alphabank.backend.decision.StopFactorSync;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.DecisionType;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.service.AutoCheckService;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.StatusTransitionService;

@Service
public class AutoCheckServiceImpl implements AutoCheckService {

    private final ForkCalculationService forkCalculationService;
    private final EnrichmentService enrichmentService;
    private final StopFactorSync stopFactorSync;
    private final AutoDecisionRule decisionRule;
    private final GridOfferWriter gridOfferWriter;
    private final SpotRepository spotRepository;
    private final OfferWriter offerWriter;
    private final DecisionWriter decisionWriter;
    private final StatusTransitionService statusTransitionService;
    private final Clock clock;

    public AutoCheckServiceImpl(ForkCalculationService forkCalculationService, EnrichmentService enrichmentService,
                                StopFactorSync stopFactorSync, AutoDecisionRule decisionRule,
                                GridOfferWriter gridOfferWriter, SpotRepository spotRepository,
                                OfferWriter offerWriter, DecisionWriter decisionWriter,
                                StatusTransitionService statusTransitionService, Clock clock) {
        this.forkCalculationService = forkCalculationService;
        this.enrichmentService = enrichmentService;
        this.stopFactorSync = stopFactorSync;
        this.decisionRule = decisionRule;
        this.gridOfferWriter = gridOfferWriter;
        this.spotRepository = spotRepository;
        this.offerWriter = offerWriter;
        this.decisionWriter = decisionWriter;
        this.statusTransitionService = statusTransitionService;
        this.clock = clock;
    }

    @Override
    public ApplicationStatus run(ApplicationRecord application, ProductRecord product, String inn) {
        ForkCalculation calculation = forkCalculationService.calculate(application, product, inn);
        enrichmentService.persistCompanySnapshot(calculation.enrichment().egrul());
        stopFactorSync.sync(application.id(), calculation.enrichment().spots());
        ScoreResponse score = calculation.score();
        ForkSnapshotRecord snapshot = forkCalculationService.save(application.id(), calculation.request(), score);
        List<SpotRecord> spots = spotRepository.findByApplicationId(application.id());
        AutoDecision decision = decisionRule.decide(spots, score);

        return switch (decision.outcome()) {
            case APPROVE -> approve(application, product, calculation, snapshot, decision.reason());
            case REJECT -> reject(application, snapshot, decision.reasonSpotId(), decision.reason());
            case OFFER_GRID -> counterOffer(application, calculation, snapshot, spots);
            case UNDERWRITING -> underwriting(application, calculation, decision.reason());
        };
    }

    private ApplicationStatus approve(ApplicationRecord application, ProductRecord product,
                                      ForkCalculation calculation, ForkSnapshotRecord snapshot, String reason) {
        offerWriter.save(application.id(), product, application.amount(), application.termMonths(), null,
                calculation.request(), calculation.score(), OfferStatus.OFFERED, now());
        decisionWriter.bySystem(application.id(), DecisionType.APPROVED, snapshot, null, reason);
        return moveTo(application, ApplicationStatus.APPROVED, reason);
    }

    private ApplicationStatus reject(ApplicationRecord application, ForkSnapshotRecord snapshot, UUID reasonSpotId,
                                     String reason) {
        decisionWriter.bySystem(application.id(), DecisionType.REJECTED, snapshot, reasonSpotId, reason);
        return moveTo(application, ApplicationStatus.REJECTED, reason);
    }

    private ApplicationStatus counterOffer(ApplicationRecord application, ForkCalculation calculation,
                                           ForkSnapshotRecord snapshot, List<SpotRecord> spots) {
        int saved = gridOfferWriter.save(application.id(), calculation.request(), OfferStatus.OFFERED);
        if (saved == 0) {
            String reason = "Ни один вариант иных условий не проходит";
            UUID reasonSpotId = decisionRule.reasonSpotId(spots);
            return reasonSpotId == null
                    ? moveTo(application, ApplicationStatus.UNDERWRITING, reason + ", пятна-причины нет")
                    : reject(application, snapshot, reasonSpotId, reason);
        }
        String reason = "Система предлагает иные условия: вариантов " + saved;
        decisionWriter.bySystem(application.id(), DecisionType.ALTERNATIVE, snapshot, null, reason);
        return moveTo(application, ApplicationStatus.COUNTER_OFFER, reason);
    }

    private ApplicationStatus underwriting(ApplicationRecord application, ForkCalculation calculation,
                                           String reason) {
        gridOfferWriter.save(application.id(), calculation.request(), OfferStatus.CALCULATED);
        return moveTo(application, ApplicationStatus.UNDERWRITING, reason);
    }

    private ApplicationStatus moveTo(ApplicationRecord application, ApplicationStatus status, String reason) {
        return statusTransitionService.apply(application.id(), status, TransitionInitiator.SYSTEM, null, reason);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}

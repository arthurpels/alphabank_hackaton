package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.decision.AutoDecision;
import com.alphabank.backend.decision.AutoDecisionRule;
import com.alphabank.backend.decision.DecisionWriter;
import com.alphabank.backend.decision.OfferWriter;
import com.alphabank.backend.decision.StopFactorSync;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.DecisionType;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.OfferGridClient;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.dto.Offer;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.scoringclient.enums.Collateral;
import com.alphabank.backend.service.AutoCheckService;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.StatusTransitionService;

@Service
public class AutoCheckServiceImpl implements AutoCheckService {

    private final ForkCalculationService forkCalculationService;
    private final EnrichmentService enrichmentService;
    private final StopFactorSync stopFactorSync;
    private final AutoDecisionRule decisionRule;
    private final OfferGridClient offerGridClient;
    private final ScoringClient scoringClient;
    private final SpotRepository spotRepository;
    private final ProductRepository productRepository;
    private final OfferWriter offerWriter;
    private final DecisionWriter decisionWriter;
    private final StatusTransitionService statusTransitionService;
    private final Clock clock;

    public AutoCheckServiceImpl(ForkCalculationService forkCalculationService, EnrichmentService enrichmentService,
                                StopFactorSync stopFactorSync, AutoDecisionRule decisionRule,
                                OfferGridClient offerGridClient, ScoringClient scoringClient,
                                SpotRepository spotRepository, ProductRepository productRepository,
                                OfferWriter offerWriter, DecisionWriter decisionWriter,
                                StatusTransitionService statusTransitionService, Clock clock) {
        this.forkCalculationService = forkCalculationService;
        this.enrichmentService = enrichmentService;
        this.stopFactorSync = stopFactorSync;
        this.decisionRule = decisionRule;
        this.offerGridClient = offerGridClient;
        this.scoringClient = scoringClient;
        this.spotRepository = spotRepository;
        this.productRepository = productRepository;
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
        forkCalculationService.save(application.id(), score);
        List<SpotRecord> spots = spotRepository.findByApplicationId(application.id());
        AutoDecision decision = decisionRule.decide(spots, score);

        return switch (decision.outcome()) {
            case APPROVE -> approve(application, product, score, decision.reason());
            case REJECT -> reject(application, score, decision.reasonSpotId(), decision.reason());
            case OFFER_GRID -> counterOffer(application, calculation, spots);
            case UNDERWRITING -> underwriting(application, calculation, decision.reason());
        };
    }

    private ApplicationStatus approve(ApplicationRecord application, ProductRecord product, ScoreResponse score,
                                      String reason) {
        offerWriter.save(application.id(), product, application.amount(), application.termMonths(), null, score,
                OfferStatus.OFFERED, now());
        decisionWriter.bySystem(application.id(), DecisionType.APPROVED, score.rulesVersion(), null, reason);
        return moveTo(application, ApplicationStatus.APPROVED, reason);
    }

    private ApplicationStatus reject(ApplicationRecord application, ScoreResponse score, UUID reasonSpotId,
                                     String reason) {
        decisionWriter.bySystem(application.id(), DecisionType.REJECTED, score.rulesVersion(), reasonSpotId, reason);
        return moveTo(application, ApplicationStatus.REJECTED, reason);
    }

    private ApplicationStatus counterOffer(ApplicationRecord application, ForkCalculation calculation,
                                           List<SpotRecord> spots) {
        int saved = saveGrid(application, calculation.request(), OfferStatus.OFFERED);
        if (saved == 0) {
            String reason = "Ни один вариант иных условий не проходит";
            UUID reasonSpotId = decisionRule.reasonSpotId(spots);
            return reasonSpotId == null
                    ? moveTo(application, ApplicationStatus.UNDERWRITING, reason + ", пятна-причины нет")
                    : reject(application, calculation.score(), reasonSpotId, reason);
        }
        String reason = "Система предлагает иные условия: вариантов " + saved;
        decisionWriter.bySystem(application.id(), DecisionType.ALTERNATIVE, calculation.score().rulesVersion(),
                null, reason);
        return moveTo(application, ApplicationStatus.COUNTER_OFFER, reason);
    }

    private ApplicationStatus underwriting(ApplicationRecord application, ForkCalculation calculation,
                                           String reason) {
        saveGrid(application, calculation.request(), OfferStatus.CALCULATED);
        return moveTo(application, ApplicationStatus.UNDERWRITING, reason);
    }

    private int saveGrid(ApplicationRecord application, ScoreRequest request, OfferStatus status) {
        List<Offer> offers = offerGridClient.grid(request).offers();
        OffsetDateTime now = now();
        for (int i = 0; i < offers.size(); i++) {
            Offer offer = offers.get(i);
            ProductRecord product = productRepository.findByCode(offer.productCode())
                    .orElseThrow(() -> new IllegalStateException("Продукт " + offer.productCode() + " не найден"));
            offerWriter.save(application.id(), product, offer.amount(), offer.termMonths(), offer.collateralType(),
                    scoringClient.score(variant(request, offer)), status, now.plusNanos(i * 1_000L));
        }
        return offers.size();
    }

    private static ScoreRequest variant(ScoreRequest request, Offer offer) {
        Collateral collateral = offer.collateralType() == null
                ? Collateral.DECLINED : Collateral.valueOf(offer.collateralType());
        return new ScoreRequest(request.asOf(), offer.productCode(), offer.amount(), offer.termMonths(),
                request.egrulStatus(), request.registeredAt(), request.directorSince(), request.massAddress(),
                request.creditScore(), request.delinq12m(), request.bkiCurrentOverdue(), request.taxDebt(),
                request.unfiledReports(), request.monthlyRevenue(), request.stability(), request.purpose(),
                collateral);
    }

    private ApplicationStatus moveTo(ApplicationRecord application, ApplicationStatus status, String reason) {
        return statusTransitionService.apply(application.id(), status, TransitionInitiator.SYSTEM, null, reason);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}

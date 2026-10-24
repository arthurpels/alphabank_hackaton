package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.model.FactorStates;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.dto.Factor;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.service.FactorStateService;
import com.alphabank.backend.service.ForkCalculationService;

@Service
public class ForkCalculationServiceImpl implements ForkCalculationService {

    private final EnrichmentService enrichmentService;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final FactorStateService factorStateService;
    private final ScoringClient scoringClient;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;
    private final OfferRepository offerRepository;
    private final Clock clock;

    public ForkCalculationServiceImpl(EnrichmentService enrichmentService,
                                      ApplicationSourceRepository applicationSourceRepository,
                                      FactorStateService factorStateService, ScoringClient scoringClient,
                                      ForkSnapshotRepository forkSnapshotRepository, SpotRepository spotRepository,
                                      SpotAnswerRepository spotAnswerRepository, OfferRepository offerRepository,
                                      Clock clock) {
        this.enrichmentService = enrichmentService;
        this.applicationSourceRepository = applicationSourceRepository;
        this.factorStateService = factorStateService;
        this.scoringClient = scoringClient;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
        this.offerRepository = offerRepository;
        this.clock = clock;
    }

    @Override
    public ForkCalculation calculate(ApplicationRecord application, ProductRecord product, String inn) {
        EnrichmentResult enrichment = enrichmentService.enrich(inn, true, LocalDate.now(clock));
        List<RevenueSourceData> connected = enrichment.availableRevenueSources().stream()
                .filter(source -> RevenueSeriesServiceImpl.REVENUE_SOURCES.contains(source.sourceCode()))
                .filter(source -> applicationSourceRepository.isConsentGiven(application.id(), source.sourceCode()))
                .toList();
        Long sourceRevenue = enrichmentService.calculateMonthlyRevenueKopecks(connected);
        Long contractRevenue = contractRevenue(spotAnswerRepository.findAcceptedContracts(application.id()));
        Long revenue = Stream.of(sourceRevenue, contractRevenue).filter(Objects::nonNull).max(Long::compare)
                .orElse(null);
        FactorStates states = factorStateService.resolve(application.id(), revenue != null, sourceRevenue == null);
        ScoreRequest request = enrichmentService.createScoreRequest(enrichment, product.code(), application.amount(),
                application.termMonths(), revenue, states.stability(), states.purpose(), states.collateral());
        return new ForkCalculation(enrichment, request, scoringClient.score(request));
    }

    public static Long contractRevenue(List<SpotAnswerRecord> contracts) {
        return contracts.stream()
                .map(contract -> contract.contractAmount() / contract.contractTermMonths())
                .max(Long::compare)
                .orElse(null);
    }

    @Override
    public void save(UUID applicationId, ScoreResponse score) {
        forkSnapshotRepository.insert(new ForkSnapshotRecord(UUID.randomUUID(), applicationId, score.rateFrom(),
                score.rateTo(), score.thresholds().approveMax(), score.thresholds().rejectMin(),
                score.rulesVersion(), OffsetDateTime.now(clock)));
        Map<String, Double> prices = spotPrices(score);
        spotRepository.findByApplicationId(applicationId).stream()
                .filter(spot -> spot.status() != SpotStatus.CLOSED && spot.scoringFactor() != null)
                .filter(spot -> prices.containsKey(spot.scoringFactor()))
                .forEach(spot -> spotRepository.updatePrice(spot.id(), prices.get(spot.scoringFactor())));
        offerRepository.expireActive(applicationId);
    }

    public static Map<String, Double> spotPrices(ScoreResponse score) {
        return score.factors().stream()
                .filter(factor -> factor.spotPricePp() != null)
                .collect(Collectors.toMap(factor -> factor.code().name().toLowerCase(), Factor::spotPricePp));
    }
}

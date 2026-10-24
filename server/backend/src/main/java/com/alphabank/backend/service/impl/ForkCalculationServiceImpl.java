package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.service.ForkCalculationService;

@Service
public class ForkCalculationServiceImpl implements ForkCalculationService {

    private static final Set<SourceCode> REVENUE_SOURCES = Set.of(SourceCode.OFD, SourceCode.MARKETPLACE,
            SourceCode.ACQUIRING);

    private final EnrichmentService enrichmentService;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final ScoringClient scoringClient;
    private final Clock clock;

    public ForkCalculationServiceImpl(EnrichmentService enrichmentService,
                                      ApplicationSourceRepository applicationSourceRepository,
                                      ScoringClient scoringClient, Clock clock) {
        this.enrichmentService = enrichmentService;
        this.applicationSourceRepository = applicationSourceRepository;
        this.scoringClient = scoringClient;
        this.clock = clock;
    }

    @Override
    public ForkCalculation calculate(ApplicationRecord application, ProductRecord product, String inn) {
        EnrichmentResult enrichment = enrichmentService.enrich(inn, true, LocalDate.now(clock));
        List<RevenueSourceData> connected = enrichment.availableRevenueSources().stream()
                .filter(source -> REVENUE_SOURCES.contains(source.sourceCode()))
                .filter(source -> applicationSourceRepository.isConsentGiven(application.id(), source.sourceCode()))
                .toList();
        ScoreRequest request = enrichmentService.createScoreRequest(enrichment, product.code(), application.amount(),
                application.termMonths(), enrichmentService.calculateMonthlyRevenueKopecks(connected),
                null, null, null);
        return new ForkCalculation(enrichment, scoringClient.score(request));
    }
}

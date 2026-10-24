package com.alphabank.backend.closer;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.AnomalyClient;
import com.alphabank.backend.scoringclient.dto.AnomaliesRequest;
import com.alphabank.backend.scoringclient.dto.Anomaly;
import com.alphabank.backend.scoringclient.dto.Series;
import com.alphabank.backend.service.RevenueSeriesService;
import com.alphabank.backend.service.impl.FactorStateServiceImpl;
import com.alphabank.backend.service.impl.RevenueSeriesServiceImpl;

@Component
public class ConnectSourceCloser implements SpotCloser {

    private static final Set<String> REVENUE_FACTORS = Set.of(FactorStateServiceImpl.DEBT_LOAD, FactorStateServiceImpl.REVENUE_STABILITY);

    private final MocksClient mocksClient;
    private final EnrichmentService enrichmentService;
    private final RevenueSeriesService revenueSeriesService;
    private final AnomalyClient anomalyClient;
    private final SpotRepository spotRepository;
    private final SpotResolutionWriter writer;
    private final Clock clock;

    public ConnectSourceCloser(MocksClient mocksClient, EnrichmentService enrichmentService,
                               RevenueSeriesService revenueSeriesService, AnomalyClient anomalyClient,
                               SpotRepository spotRepository, SpotResolutionWriter writer, Clock clock) {
        this.mocksClient = mocksClient;
        this.enrichmentService = enrichmentService;
        this.revenueSeriesService = revenueSeriesService;
        this.anomalyClient = anomalyClient;
        this.spotRepository = spotRepository;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public CloseMethod method() {
        return CloseMethod.CONNECT_SOURCE;
    }

    @Override
    public void close(SpotCloseContext context) {
        SourceCode source = context.request().sourceCode();
        if (!RevenueSeriesServiceImpl.REVENUE_SOURCES.contains(source)) {
            throw ApiException.validation("Подключить можно только источник выручки: OFD, MARKETPLACE или ACQUIRING");
        }
        String inn = context.inn();
        switch (source) {
            case OFD -> mocksClient.ofdConnect(inn);
            case MARKETPLACE -> mocksClient.marketplaceConnect(inn);
            default -> mocksClient.acquiringConnect(inn);
        }
        Series series = revenueSeriesService.fetch(source, inn);
        UUID applicationId = context.application().id();
        enrichmentService.recordConsent(applicationId, source, true);

        spotRepository.findByApplicationId(applicationId).stream()
                .filter(spot -> spot.type() == SpotType.NO_DATA && spot.status() == SpotStatus.OPEN)
                .filter(spot -> REVENUE_FACTORS.contains(spot.scoringFactor()))
                .forEach(spot -> writer.close(spot, context.user().id(), "Подключён источник " + source));

        List<Anomaly> anomalies = anomalyClient.detect(new AnomaliesRequest(List.of(series))).anomalies();
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (int i = 0; i < anomalies.size(); i++) {
            Anomaly anomaly = anomalies.get(i);
            writer.create(new SpotRecord(UUID.randomUUID(), applicationId, SpotType.ANOMALY, anomaly.title(),
                    anomaly.clientHint(), FactorStateServiceImpl.REVENUE_STABILITY, null, CloseMethod.ANSWER,
                    SpotStatus.OPEN, DecidedBy.SYSTEM, anomaly.source(), null, anomaly.answerOptions(),
                    now.plusNanos(i * 1_000L), null), anomaly.title());
        }
    }
}

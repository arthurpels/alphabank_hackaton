package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.enrichment.client.MockNotFoundException;
import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.scoringclient.dto.Series;
import com.alphabank.backend.scoringclient.dto.SeriesPoint;
import com.alphabank.backend.service.RevenueSeriesService;

@Service
public class RevenueSeriesServiceImpl implements RevenueSeriesService {

    public static final List<SourceCode> REVENUE_SOURCES = List.of(SourceCode.OFD, SourceCode.MARKETPLACE,
            SourceCode.ACQUIRING);

    private final MocksClient mocksClient;
    private final ApplicationSourceRepository applicationSourceRepository;

    public RevenueSeriesServiceImpl(MocksClient mocksClient, ApplicationSourceRepository applicationSourceRepository) {
        this.mocksClient = mocksClient;
        this.applicationSourceRepository = applicationSourceRepository;
    }

    @Override
    public Series fetch(SourceCode source, String inn) {
        List<SeriesPoint> points = switch (source) {
            case OFD -> mocksClient.ofdRevenue(inn).map(ofd -> ofd.months().stream()
                    .map(m -> new SeriesPoint(m.month(), m.revenue() * 100)).toList()).orElse(null);
            case MARKETPLACE -> mocksClient.marketplaceSales(inn).map(wb -> wb.months().stream()
                    .map(m -> new SeriesPoint(m.month(), m.payout() * 100)).toList()).orElse(null);
            case ACQUIRING -> mocksClient.acquiringTurnover(inn).map(acq -> acq.months().stream()
                    .map(m -> new SeriesPoint(m.month(), m.turnover() * 100)).toList()).orElse(null);
            default -> throw ApiException.validation("Источник " + source + " не даёт выручку");
        };
        if (points == null) {
            throw new MockNotFoundException("Источник " + source + " не нашёл данных по вашему ИНН");
        }
        return new Series(source, points);
    }

    @Override
    public List<Series> fetchConnected(UUID applicationId, String inn) {
        return REVENUE_SOURCES.stream()
                .filter(source -> applicationSourceRepository.isConsentGiven(applicationId, source))
                .map(source -> fetch(source, inn))
                .toList();
    }
}

package com.alphabank.backend.closer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.alphabank.backend.dto.response.AnomalyVerdictDto;
import com.alphabank.backend.dto.response.AnswerClassificationDto;
import com.alphabank.backend.dto.response.AnswerOptionDto;
import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.model.ResolvedAnomalyAnswer;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.AnomalyClient;
import com.alphabank.backend.scoringclient.AnswerClient;
import com.alphabank.backend.scoringclient.dto.AnomaliesResponse;
import com.alphabank.backend.scoringclient.dto.AnswerClassifyResponse;
import com.alphabank.backend.scoringclient.dto.Anomaly;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyResponse;
import com.alphabank.backend.scoringclient.dto.Series;
import com.alphabank.backend.scoringclient.enums.AnswerCheckVerdict;
import com.alphabank.backend.scoringclient.enums.AnswerClassifyStatus;
import com.alphabank.backend.scoringclient.enums.AnswerCode;
import com.alphabank.backend.service.RevenueSeriesService;

class AnomalyAnswerResolverTest {

    private static final String TITLE = "Почему в марте выручка упала на 45 %";
    private static final String INN = "7701234560";

    private final AnswerClient answerClient = mock(AnswerClient.class);
    private final AnomalyClient anomalyClient = mock(AnomalyClient.class);
    private final RevenueSeriesService revenueSeriesService = mock(RevenueSeriesService.class);
    private final SpotAnswerRepository spotAnswerRepository = mock(SpotAnswerRepository.class);
    private final AnomalyAnswerResolver resolver = new AnomalyAnswerResolver(answerClient, anomalyClient,
            revenueSeriesService, spotAnswerRepository);
    private final SpotRecord spot = new SpotRecord(UUID.randomUUID(), UUID.randomUUID(), SpotType.ANOMALY, TITLE,
            null, "revenue_stability", null, CloseMethod.ANSWER, SpotStatus.OPEN, DecidedBy.SYSTEM, SourceCode.OFD,
            null, List.of(new AnswerOptionDto("RENOVATION", "Был ремонт")), OffsetDateTime.now(), null);

    @Test
    void sameTextReusesStoredClassificationAndDoesNotCallModel() {
        Series series = new Series(SourceCode.OFD, List.of());
        when(revenueSeriesService.fetch(SourceCode.OFD, INN)).thenReturn(series);
        when(revenueSeriesService.fetchHistory(SourceCode.OFD, INN)).thenReturn(series);
        when(anomalyClient.detect(any())).thenReturn(new AnomaliesResponse(List.of(
                new Anomaly("REVENUE_DROP", SourceCode.OFD, "2026-03", -45, TITLE, null, List.of()))));
        AnswerClassificationDto stored = new AnswerClassificationDto(AnswerClassifyStatus.DECIDED,
                AnswerCode.OTHER, "LLM", List.of(), "Причина не из списка", null, null, null);
        when(spotAnswerRepository.findBySpotId(spot.id())).thenReturn(List.of(new SpotAnswerRecord(UUID.randomUUID(),
                spot.id(), "OTHER", "Владелец болел, магазин не работал", null, null, null, AnswerVerdict.REJECTED,
                "underwriter", "Нужны подробности", OffsetDateTime.now(), OffsetDateTime.now(), null,
                new AnomalyVerdictDto(null, AnswerCode.OTHER, stored, null))));
        when(answerClient.verify(any())).thenReturn(new AnswerVerifyResponse(AnswerCheckVerdict.REVIEW, null, "нет"));

        ResolvedAnomalyAnswer resolved = resolver.resolve(spot, null, "  владелец БОЛЕЛ, магазин не работал ", INN);

        assertThat(resolved.reused()).isTrue();
        assertThat(resolved.verdict().classification()).isEqualTo(stored);
        assertThat(resolved.verdict().anomaly().month()).isEqualTo("2026-03");
        verify(answerClient, never()).classify(any());
    }

    @Test
    void unavailableClassificationIsNotReused() {
        when(revenueSeriesService.fetch(SourceCode.OFD, INN)).thenReturn(new Series(SourceCode.OFD, List.of()));
        when(anomalyClient.detect(any())).thenReturn(new AnomaliesResponse(List.of()));
        AnswerClassificationDto unavailable = new AnswerClassificationDto(AnswerClassifyStatus.UNAVAILABLE, null,
                "KEYWORDS", List.of(), null, null, null, null);
        when(spotAnswerRepository.findBySpotId(spot.id())).thenReturn(List.of(new SpotAnswerRecord(UUID.randomUUID(),
                spot.id(), null, "печь сломалась", null, null, null, AnswerVerdict.REJECTED, "underwriter", "?",
                OffsetDateTime.now(), OffsetDateTime.now(), null,
                new AnomalyVerdictDto(null, null, unavailable, null))));
        when(answerClient.classify(any())).thenReturn(new AnswerClassifyResponse(
                AnswerClassifyStatus.DECIDED, AnswerCode.RENOVATION, "LLM", List.of(), null, "печь", null, null));

        ResolvedAnomalyAnswer resolved = resolver.resolve(spot, null, "печь сломалась", INN);

        assertThat(resolved.reused()).isFalse();
        assertThat(resolved.verdict().code()).isEqualTo(AnswerCode.RENOVATION);
        assertThat(resolved.verdict().anomaly()).isNull();
        assertThat(resolved.verdict().check()).isNull();
        verify(answerClient, never()).verify(any());
    }
}

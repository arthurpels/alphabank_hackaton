package com.alphabank.backend.closer;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import com.alphabank.backend.dto.response.AnomalyRefDto;
import com.alphabank.backend.dto.response.AnomalyVerdictDto;
import com.alphabank.backend.dto.response.AnswerCheckDto;
import com.alphabank.backend.dto.response.AnswerClassificationDto;
import com.alphabank.backend.dto.response.AnswerOptionDto;
import com.alphabank.backend.dto.response.LlmMetaDto;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.model.ResolvedAnomalyAnswer;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.AnomalyClient;
import com.alphabank.backend.scoringclient.AnswerClient;
import com.alphabank.backend.scoringclient.dto.AnomaliesRequest;
import com.alphabank.backend.scoringclient.dto.AnomalyRef;
import com.alphabank.backend.scoringclient.dto.AnswerClassifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerClassifyResponse;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyResponse;
import com.alphabank.backend.scoringclient.enums.AnswerClassifyStatus;
import com.alphabank.backend.scoringclient.enums.AnswerCode;
import com.alphabank.backend.service.RevenueSeriesService;
import com.alphabank.backend.util.TextNormalizer;

@Component
public class AnomalyAnswerResolver {

    private static final Logger log = LoggerFactory.getLogger(AnomalyAnswerResolver.class);

    private final AnswerClient answerClient;
    private final AnomalyClient anomalyClient;
    private final RevenueSeriesService revenueSeriesService;
    private final SpotAnswerRepository spotAnswerRepository;

    public AnomalyAnswerResolver(AnswerClient answerClient, AnomalyClient anomalyClient,
                                 RevenueSeriesService revenueSeriesService,
                                 SpotAnswerRepository spotAnswerRepository) {
        this.answerClient = answerClient;
        this.anomalyClient = anomalyClient;
        this.revenueSeriesService = revenueSeriesService;
        this.spotAnswerRepository = spotAnswerRepository;
    }

    public ResolvedAnomalyAnswer resolve(SpotRecord spot, String optionCode, String text, String inn) {
        AnomalyRef anomaly = anomaly(spot, inn).orElse(null);
        AnswerClassificationDto classification = null;
        boolean reused = false;
        AnswerCode code;
        if (optionCode != null) {
            code = option(spot, optionCode);
        } else {
            Optional<AnswerClassificationDto> stored = stored(spot, text);
            reused = stored.isPresent();
            classification = stored.orElseGet(() -> classify(text, anomaly));
            code = classification.code();
        }
        AnswerCheckDto check = code == null || anomaly == null ? null : verify(spot, inn, anomaly, code);
        return new ResolvedAnomalyAnswer(new AnomalyVerdictDto(toDto(anomaly), code, classification, check), reused);
    }

    private Optional<AnomalyRef> anomaly(SpotRecord spot, String inn) {
        if (spot.source() == null) {
            return Optional.empty();
        }
        return anomalyClient.detect(new AnomaliesRequest(List.of(revenueSeriesService.fetch(spot.source(), inn))))
                .anomalies().stream()
                .filter(found -> found.title().equals(spot.title()))
                .findFirst()
                .map(found -> new AnomalyRef(found.code(), found.month(), found.changePct(), found.source()));
    }

    private static AnswerCode option(SpotRecord spot, String optionCode) {
        boolean offered = spot.answerOptions() != null && spot.answerOptions().stream()
                .map(AnswerOptionDto::code)
                .anyMatch(optionCode::equals);
        if (!offered || Arrays.stream(AnswerCode.values()).noneMatch(code -> code.name().equals(optionCode))) {
            throw ApiException.validation("Вариант " + optionCode + " не предлагается для этого пятна");
        }
        return AnswerCode.valueOf(optionCode);
    }

    private Optional<AnswerClassificationDto> stored(SpotRecord spot, String text) {
        String normalized = TextNormalizer.normalize(text);
        return spotAnswerRepository.findBySpotId(spot.id()).stream()
                .filter(answer -> answer.anomalyVerdict() != null && answer.anomalyVerdict().classification() != null)
                .filter(answer -> answer.anomalyVerdict().classification().status() != AnswerClassifyStatus.UNAVAILABLE)
                .filter(answer -> TextNormalizer.normalize(answer.text()).equals(normalized))
                .map(SpotAnswerRecord::anomalyVerdict)
                .map(AnomalyVerdictDto::classification)
                .reduce((first, second) -> second);
    }

    private AnswerClassificationDto classify(String text, AnomalyRef anomaly) {
        try {
            return toDto(answerClient.classify(new AnswerClassifyRequest(text, anomaly)));
        } catch (RestClientException e) {
            log.warn("ML недоступна при разборе ответа на аномалию: {}", e.getMessage());
            return new AnswerClassificationDto(AnswerClassifyStatus.UNAVAILABLE, null, "KEYWORDS", List.of(),
                    "ML временно недоступна", null, null, null);
        }
    }

    private AnswerCheckDto verify(SpotRecord spot, String inn, AnomalyRef anomaly, AnswerCode code) {
        try {
            AnswerVerifyResponse response = answerClient.verify(new AnswerVerifyRequest(anomaly, code,
                    List.of(revenueSeriesService.fetchHistory(spot.source(), inn))));
            return new AnswerCheckDto(response.verdict(), response.rule(), response.explanation());
        } catch (RestClientException e) {
            log.warn("ML недоступна при проверке ответа на аномалию: {}", e.getMessage());
            return null;
        }
    }

    private static AnswerClassificationDto toDto(AnswerClassifyResponse response) {
        LlmMetaDto llm = response.llm() == null ? null : new LlmMetaDto(response.llm().model(),
                response.llm().digest(), response.llm().promptVersion(), response.llm().cached());
        return new AnswerClassificationDto(response.status(), response.code(), response.generatedBy(),
                response.matchedKeywords() == null ? List.of() : response.matchedKeywords(), response.reason(),
                response.evidence(), response.violation(), llm);
    }

    private static AnomalyRefDto toDto(AnomalyRef anomaly) {
        return anomaly == null ? null
                : new AnomalyRefDto(anomaly.code(), anomaly.month(), anomaly.changePct(), anomaly.source());
    }
}

package com.alphabank.backend.support;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.client.ResourceAccessException;

import com.alphabank.backend.scoringclient.AnswerClient;
import com.alphabank.backend.scoringclient.dto.AnswerClassifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerClassifyResponse;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyResponse;
import com.alphabank.backend.scoringclient.dto.SeriesPoint;
import com.alphabank.backend.scoringclient.enums.AnswerCheckVerdict;
import com.alphabank.backend.scoringclient.enums.AnswerClassifyStatus;
import com.alphabank.backend.scoringclient.enums.AnswerCode;

public class CountingAnswerClient implements AnswerClient {

    private final AtomicInteger classifyCalls = new AtomicInteger();
    private volatile AnswerVerifyRequest lastVerify;

    @Override
    public AnswerClassifyResponse classify(AnswerClassifyRequest request) {
        classifyCalls.incrementAndGet();
        String text = request.text().toLowerCase();
        if (text.contains("игнорируй")) {
            return new AnswerClassifyResponse(AnswerClassifyStatus.DECIDED, AnswerCode.OTHER, "KEYWORDS",
                    List.of("игнорируй"), null, null, "MANIPULATION", null);
        }
        if (text.contains("ремонт")) {
            return keywords(AnswerCode.RENOVATION, "ремонт");
        }
        if (text.contains("сезон")) {
            return keywords(AnswerCode.SEASONAL, "сезон");
        }
        if (text.contains("печь")) {
            return new AnswerClassifyResponse(AnswerClassifyStatus.DECIDED, AnswerCode.RENOVATION, "LLM", List.of(),
                    "Поломка основного оборудования", "Печь вышла из строя", null, CountingPurposeClient.LLM);
        }
        if (text.contains("недоступн")) {
            return new AnswerClassifyResponse(AnswerClassifyStatus.UNAVAILABLE, null, "KEYWORDS", List.of(), null,
                    null, null, null);
        }
        if (text.contains("таймаут")) {
            throw new ResourceAccessException("Read timed out");
        }
        return new AnswerClassifyResponse(AnswerClassifyStatus.DECIDED, AnswerCode.OTHER, "LLM", List.of(),
                "Причина не из списка", null, null, CountingPurposeClient.LLM);
    }

    @Override
    public AnswerVerifyResponse verify(AnswerVerifyRequest request) {
        lastVerify = request;
        if (request.answerCode() == AnswerCode.RENOVATION && recovered(request)) {
            return new AnswerVerifyResponse(AnswerCheckVerdict.ACCEPTED, "RENOVATION_ONE_OFF",
                    "падение разовое, в следующем месяце выручка восстановилась");
        }
        if (request.answerCode() == AnswerCode.RENOVATION || request.answerCode() == AnswerCode.SEASONAL) {
            return new AnswerVerifyResponse(AnswerCheckVerdict.REVIEW,
                    request.answerCode() == AnswerCode.RENOVATION ? "RENOVATION_ONE_OFF" : "SEASONAL_LAST_YEAR",
                    "данные не подтверждают ответ");
        }
        return new AnswerVerifyResponse(AnswerCheckVerdict.REVIEW, null, "проверки данными для этой причины нет");
    }

    public int classifyCalls() {
        return classifyCalls.get();
    }

    public AnswerVerifyRequest lastVerify() {
        return lastVerify;
    }

    public void reset() {
        classifyCalls.set(0);
        lastVerify = null;
    }

    private static boolean recovered(AnswerVerifyRequest request) {
        List<SeriesPoint> points = request.series().get(0).points().stream()
                .sorted(Comparator.comparing(SeriesPoint::month)).toList();
        for (int i = 1; i < points.size() - 1; i++) {
            if (points.get(i).month().equals(request.anomaly().month())) {
                return points.get(i + 1).value() >= points.get(i - 1).value() * 0.9;
            }
        }
        return false;
    }

    private static AnswerClassifyResponse keywords(AnswerCode code, String keyword) {
        return new AnswerClassifyResponse(AnswerClassifyStatus.DECIDED, code, "KEYWORDS", List.of(keyword), null,
                null, null, null);
    }
}

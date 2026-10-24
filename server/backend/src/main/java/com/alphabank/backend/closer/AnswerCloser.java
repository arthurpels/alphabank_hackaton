package com.alphabank.backend.closer;

import java.util.List;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.dto.response.PurposeVerdictDto;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.model.ResolvedPurpose;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.AnomalyClient;
import com.alphabank.backend.scoringclient.dto.AnomaliesRequest;
import com.alphabank.backend.scoringclient.dto.Anomaly;
import com.alphabank.backend.scoringclient.dto.Series;
import com.alphabank.backend.scoringclient.dto.SeriesPoint;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;
import com.alphabank.backend.service.RevenueSeriesService;
import com.alphabank.backend.service.impl.FactorStateServiceImpl;

@Component
public class AnswerCloser implements SpotCloser {

    static final String RENOVATION = "RENOVATION";
    private static final String REVENUE_DROP = "REVENUE_DROP";
    private static final double RECOVERY_SHARE = 0.9;
    private static final String UNCLEAR_GOAL = "Не удалось понять цель — напишите подробнее, что именно покупаете";

    private final PurposeVerdictResolver purposeVerdictResolver;
    private final AnomalyClient anomalyClient;
    private final RevenueSeriesService revenueSeriesService;
    private final SpotResolutionWriter writer;

    public AnswerCloser(PurposeVerdictResolver purposeVerdictResolver, AnomalyClient anomalyClient,
                        RevenueSeriesService revenueSeriesService, SpotResolutionWriter writer) {
        this.purposeVerdictResolver = purposeVerdictResolver;
        this.anomalyClient = anomalyClient;
        this.revenueSeriesService = revenueSeriesService;
        this.writer = writer;
    }

    @Override
    public CloseMethod method() {
        return CloseMethod.ANSWER;
    }

    @Override
    public void close(SpotCloseContext context) {
        SpotRecord spot = context.spot();
        SpotCloseRequest request = context.request();
        if (FactorStateServiceImpl.PURPOSE.equals(spot.scoringFactor())) {
            closePurpose(context);
        } else if (spot.type() == SpotType.ANOMALY
                && FactorStateServiceImpl.REVENUE_STABILITY.equals(spot.scoringFactor())
                && RENOVATION.equals(request.optionCode())
                && singleDropRecovered(context)) {
            writer.acceptAnswer(spot, context.user().id(), request.optionCode(), request.text(),
                    request.documentId(), "Ответ подтверждён данными: падение разовое, выручка восстановилась");
        } else {
            writer.sendToReview(spot, context.user().id(), request.optionCode(), request.text(),
                    request.documentId());
        }
    }

    private void closePurpose(SpotCloseContext context) {
        SpotCloseRequest request = context.request();
        String goal = request.text() != null ? request.text() : request.optionCode();
        ResolvedPurpose resolved = purposeVerdictResolver.resolve(context.spot(), context.product().code(), goal);
        PurposeVerdictDto verdict = resolved.verdict();
        String source = source(resolved);
        if (verdict.status() == PurposeStatus.NOT_APPLICABLE) {
            writer.acceptPurpose(context.spot(), context.user().id(), null, goal, verdict, resolved.reused(),
                    "Цель для этого продукта не учитывается — " + source);
        } else if (verdict.status() == PurposeStatus.UNAVAILABLE) {
            writer.sendPurposeToReview(context.spot(), context.user().id(), goal, verdict, resolved.reused(),
                    "LLM недоступна — ответ передан на проверку специалисту");
        } else if (verdict.purpose() == null) {
            writer.rejectPurpose(context.spot(), context.user().id(), goal, verdict, resolved.reused(),
                    UNCLEAR_GOAL, UNCLEAR_GOAL + " — " + source);
        } else {
            writer.acceptPurpose(context.spot(), context.user().id(), verdict.purpose().name(), goal, verdict,
                    resolved.reused(), "Цель кредита: " + goal + " — " + source);
        }
    }

    private static String source(ResolvedPurpose resolved) {
        PurposeVerdictDto verdict = resolved.verdict();
        String decided = verdict.llm() != null
                ? "решила LLM " + verdict.llm().model() + ", промпт " + verdict.llm().promptVersion()
                : verdict.status() == PurposeStatus.UNAVAILABLE ? "LLM недоступна" : "решило правило: словарь целей";
        return resolved.reused() ? decided + "; вердикт сохранён ранее, модель не вызывалась" : decided;
    }

    private boolean singleDropRecovered(SpotCloseContext context) {
        List<Series> series = revenueSeriesService.fetchConnected(context.application().id(), context.inn());
        for (Series serie : series) {
            List<Anomaly> drops = anomalyClient.detect(new AnomaliesRequest(List.of(serie))).anomalies().stream()
                    .filter(anomaly -> REVENUE_DROP.equals(anomaly.code()))
                    .toList();
            if (drops.size() == 1 && recovered(serie.points(), drops.get(0).month())) {
                return true;
            }
        }
        return false;
    }

    private static boolean recovered(List<SeriesPoint> points, String dropMonth) {
        List<SeriesPoint> sorted = points.stream().sorted((a, b) -> a.month().compareTo(b.month())).toList();
        for (int i = 1; i < sorted.size() - 1; i++) {
            if (sorted.get(i).month().equals(dropMonth)) {
                return sorted.get(i + 1).value() >= sorted.get(i - 1).value() * RECOVERY_SHARE;
            }
        }
        return false;
    }
}

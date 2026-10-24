package com.alphabank.backend.closer;

import java.util.List;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.AnomalyClient;
import com.alphabank.backend.scoringclient.PurposeClient;
import com.alphabank.backend.scoringclient.dto.AnomaliesRequest;
import com.alphabank.backend.scoringclient.dto.Anomaly;
import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;
import com.alphabank.backend.scoringclient.dto.Series;
import com.alphabank.backend.scoringclient.dto.SeriesPoint;
import com.alphabank.backend.service.RevenueSeriesService;
import com.alphabank.backend.service.impl.FactorStateServiceImpl;

@Component
public class AnswerCloser implements SpotCloser {

    static final String RENOVATION = "RENOVATION";
    private static final String REVENUE_DROP = "REVENUE_DROP";
    private static final double RECOVERY_SHARE = 0.9;

    private final PurposeClient purposeClient;
    private final AnomalyClient anomalyClient;
    private final RevenueSeriesService revenueSeriesService;
    private final SpotResolutionWriter writer;

    public AnswerCloser(PurposeClient purposeClient, AnomalyClient anomalyClient,
                        RevenueSeriesService revenueSeriesService, SpotResolutionWriter writer) {
        this.purposeClient = purposeClient;
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
        PurposeResponse result = purposeClient.classify(new PurposeRequest(context.product().code(), goal));
        if (!result.applicable()) {
            writer.acceptAnswer(context.spot(), context.user().id(), null, goal, null,
                    "Цель для этого продукта не учитывается");
        } else if (result.purpose() == null) {
            writer.rejectAnswer(context.spot(), context.user().id(), null, goal,
                    "Не удалось понять цель — напишите подробнее, что именно покупаете");
        } else {
            writer.acceptAnswer(context.spot(), context.user().id(), result.purpose().name(), goal, null,
                    "Цель кредита: " + goal);
        }
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

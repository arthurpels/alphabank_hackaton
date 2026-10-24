package com.alphabank.backend.decision;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;

@Component
public class AutoDecisionRule {

    private static final String DEBT_LOAD = "debt_load";

    public AutoDecision decide(List<SpotRecord> spots, ScoreResponse score) {
        Optional<SpotRecord> hardStop = openStopFactor(spots, false);
        if (hardStop.isPresent()) {
            return reject(hardStop.get(), "Неустранимый стоп-фактор: " + hardStop.get().title());
        }
        Optional<SpotRecord> softStop = openStopFactor(spots, true);
        if (softStop.isPresent()) {
            return reject(softStop.get(), "Не устранён стоп-фактор: " + softStop.get().title());
        }
        double approveMax = score.thresholds().approveMax();
        if (score.policy().ok() && score.rateTo() <= approveMax) {
            return new AutoDecision(AutoDecisionOutcome.APPROVE, null,
                    "Правый край вилки " + score.rateTo() + " % ≤ порога одобрения " + approveMax + " %");
        }
        if (score.rateFrom() >= score.thresholds().rejectMin()) {
            String reason = "Левый край вилки " + score.rateFrom() + " % ≥ порога отказа "
                    + score.thresholds().rejectMin() + " %";
            UUID reasonSpotId = reasonSpotId(spots);
            return reasonSpotId == null
                    ? new AutoDecision(AutoDecisionOutcome.UNDERWRITING, null, reason + ", пятна-причины нет")
                    : new AutoDecision(AutoDecisionOutcome.REJECT, reasonSpotId, reason);
        }
        if (!score.policy().ok() || score.rateFrom() > approveMax) {
            return new AutoDecision(AutoDecisionOutcome.OFFER_GRID, null,
                    "Запрошенные условия не проходят — ищем иные условия");
        }
        return new AutoDecision(AutoDecisionOutcome.UNDERWRITING, null,
                "Вилка пересекает порог одобрения " + approveMax + " %");
    }

    public UUID reasonSpotId(List<SpotRecord> spots) {
        return spots.stream()
                .filter(spot -> spot.status() != SpotStatus.CLOSED && spot.pricePp() != null)
                .max(Comparator.comparingDouble(SpotRecord::pricePp))
                .or(() -> spots.stream().filter(spot -> DEBT_LOAD.equals(spot.scoringFactor())).findFirst())
                .or(() -> spots.stream().findFirst())
                .map(SpotRecord::id)
                .orElse(null);
    }

    private static Optional<SpotRecord> openStopFactor(List<SpotRecord> spots, boolean removable) {
        return spots.stream()
                .filter(spot -> spot.type() == SpotType.STOP_FACTOR && spot.status() != SpotStatus.CLOSED)
                .filter(spot -> Boolean.TRUE.equals(spot.removable()) == removable)
                .findFirst();
    }

    private static AutoDecision reject(SpotRecord spot, String reason) {
        return new AutoDecision(AutoDecisionOutcome.REJECT, spot.id(), reason);
    }
}

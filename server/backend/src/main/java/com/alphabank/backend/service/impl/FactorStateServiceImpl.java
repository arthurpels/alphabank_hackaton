package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.model.FactorStates;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.enums.Collateral;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.Stability;
import com.alphabank.backend.service.FactorStateService;

@Service
public class FactorStateServiceImpl implements FactorStateService {

    public static final String REVENUE_STABILITY = "revenue_stability";
    public static final String PURPOSE = "purpose";
    public static final String COLLATERAL = "collateral";

    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;

    public FactorStateServiceImpl(SpotRepository spotRepository, SpotAnswerRepository spotAnswerRepository) {
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
    }

    @Override
    public FactorStates resolve(UUID applicationId, boolean revenueConfirmed) {
        List<SpotRecord> spots = spotRepository.findByApplicationId(applicationId);
        Map<UUID, SpotAnswerRecord> answers = spotAnswerRepository.findLatestBySpotIds(
                spots.stream().map(SpotRecord::id).toList());
        return new FactorStates(
                revenueConfirmed ? stability(spots, answers) : null,
                closedAnswerCode(spots, answers, PURPOSE).map(Purpose::valueOf).orElse(null),
                closedAnswerCode(spots, answers, COLLATERAL).map(Collateral::valueOf).orElse(null));
    }

    private static Stability stability(List<SpotRecord> spots, Map<UUID, SpotAnswerRecord> answers) {
        List<SpotRecord> anomalies = spots.stream()
                .filter(spot -> spot.type() == SpotType.ANOMALY && REVENUE_STABILITY.equals(spot.scoringFactor()))
                .toList();
        if (anomalies.isEmpty()) {
            return Stability.CLEAN;
        }
        boolean rejected = anomalies.stream().anyMatch(spot -> spot.status() == SpotStatus.OPEN
                && answers.containsKey(spot.id()) && answers.get(spot.id()).verdict() == AnswerVerdict.REJECTED);
        if (rejected) {
            return Stability.REJECTED;
        }
        boolean allClosed = anomalies.stream().allMatch(spot -> spot.status() == SpotStatus.CLOSED);
        return allClosed ? Stability.EXPLAINED : Stability.ANOMALY_OPEN;
    }

    private static Optional<String> closedAnswerCode(List<SpotRecord> spots, Map<UUID, SpotAnswerRecord> answers,
                                                     String factor) {
        return spots.stream()
                .filter(spot -> factor.equals(spot.scoringFactor()) && spot.status() == SpotStatus.CLOSED)
                .map(spot -> answers.get(spot.id()))
                .filter(answer -> answer != null && answer.verdict() == AnswerVerdict.ACCEPTED
                        && answer.optionCode() != null)
                .map(SpotAnswerRecord::optionCode)
                .findFirst();
    }
}

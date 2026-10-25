package com.alphabank.backend.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.model.SnapshotTrace;
import com.alphabank.backend.model.UsedVerdict;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.MlVersionClient;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.service.ForkSnapshotService;
import com.alphabank.backend.util.CanonicalJson;

@Service
public class ForkSnapshotServiceImpl implements ForkSnapshotService {

    private final ForkSnapshotRepository forkSnapshotRepository;
    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;
    private final MlVersionClient mlVersionClient;
    private final CanonicalJson canonicalJson;

    public ForkSnapshotServiceImpl(ForkSnapshotRepository forkSnapshotRepository, SpotRepository spotRepository,
                                   SpotAnswerRepository spotAnswerRepository, MlVersionClient mlVersionClient,
                                   CanonicalJson canonicalJson) {
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
        this.mlVersionClient = mlVersionClient;
        this.canonicalJson = canonicalJson;
    }

    @Override
    public ForkSnapshotRecord save(UUID applicationId, ScoreRequest request, ScoreResponse score,
                                   OffsetDateTime calculatedAt) {
        String scoreRequest = canonicalJson.write(request);
        SnapshotTrace trace = new SnapshotTrace(scoreRequest, canonicalJson.sha256(scoreRequest),
                canonicalJson.write(score), mlVersionClient.engineVersion().orElse(null),
                usedVerdicts(applicationId));
        ForkSnapshotRecord snapshot = new ForkSnapshotRecord(UUID.randomUUID(), applicationId, score.rateFrom(),
                score.rateTo(), score.thresholds().approveMax(), score.thresholds().rejectMin(),
                score.rulesVersion(), request.asOf(), calculatedAt, null, trace);
        forkSnapshotRepository.insert(snapshot);
        return snapshot;
    }

    private List<UsedVerdict> usedVerdicts(UUID applicationId) {
        List<SpotRecord> spots = spotRepository.findByApplicationId(applicationId);
        Map<UUID, SpotAnswerRecord> answers = spotAnswerRepository.findLatestBySpotIds(
                spots.stream().map(SpotRecord::id).toList());
        return spots.stream()
                .filter(spot -> answers.containsKey(spot.id()) && answers.get(spot.id()).purposeVerdict() != null)
                .map(spot -> new UsedVerdict(spot.id(), spot.scoringFactor(),
                        answers.get(spot.id()).purposeVerdict()))
                .toList();
    }
}

package com.alphabank.backend.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.FieldDiffDto;
import com.alphabank.backend.dto.response.ForkVerificationDto;
import com.alphabank.backend.dto.response.UsedVerdictDto;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.model.SnapshotTrace;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.scoringclient.MlVersionClient;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.ForkVerificationService;
import com.alphabank.backend.util.CanonicalJson;
import com.alphabank.backend.util.JsonDiff;
import com.fasterxml.jackson.databind.node.TextNode;

@Service
public class ForkVerificationServiceImpl implements ForkVerificationService {

    private static final String REQUEST_HASH = "requestHash";

    private final ApplicationAccessService accessService;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final ScoringClient scoringClient;
    private final MlVersionClient mlVersionClient;
    private final CanonicalJson canonicalJson;

    public ForkVerificationServiceImpl(ApplicationAccessService accessService,
                                       ForkSnapshotRepository forkSnapshotRepository, ScoringClient scoringClient,
                                       MlVersionClient mlVersionClient, CanonicalJson canonicalJson) {
        this.accessService = accessService;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.scoringClient = scoringClient;
        this.mlVersionClient = mlVersionClient;
        this.canonicalJson = canonicalJson;
    }

    @Override
    public ForkVerificationDto verify(CurrentUser user, UUID applicationId) {
        accessService.load(user, applicationId);
        ForkSnapshotRecord snapshot = forkSnapshotRepository.findLatest(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "У заявки ещё нет снимка вилки"));
        SnapshotTrace trace = snapshot.trace();
        if (trace == null) {
            throw new ApiException(ErrorCode.NOT_FOUND,
                    "Снимок вилки " + snapshot.id() + " сохранён без входа скоринга, пересчитайте заявку");
        }
        List<FieldDiffDto> diff = new ArrayList<>();
        String requestHash = canonicalJson.sha256(trace.scoreRequest());
        if (!requestHash.equals(trace.requestHash())) {
            diff.add(new FieldDiffDto(REQUEST_HASH, TextNode.valueOf(trace.requestHash()),
                    TextNode.valueOf(requestHash)));
        }
        String scoreResponse = canonicalJson.write(
                scoringClient.score(canonicalJson.read(trace.scoreRequest(), ScoreRequest.class)));
        if (!scoreResponse.equals(trace.scoreResponse())) {
            List<FieldDiffDto> responseDiff = JsonDiff.diff(canonicalJson.read(trace.scoreResponse()),
                    canonicalJson.read(scoreResponse));
            diff.addAll(responseDiff.isEmpty()
                    ? List.of(new FieldDiffDto(JsonDiff.ROOT, TextNode.valueOf(trace.scoreResponse()),
                            TextNode.valueOf(scoreResponse)))
                    : responseDiff);
        }
        List<UsedVerdictDto> verdicts = trace.verdicts().stream()
                .map(verdict -> new UsedVerdictDto(verdict.spotId(), verdict.scoringFactor(), verdict.verdict()))
                .toList();
        return new ForkVerificationDto(snapshot.id(), diff.isEmpty(), snapshot.rulesVersion(),
                trace.engineVersion(), mlVersionClient.engineVersion().orElse(null), trace.requestHash(),
                snapshot.calculatedAt(), verdicts, diff);
    }
}

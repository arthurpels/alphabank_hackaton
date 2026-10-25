package com.alphabank.backend.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;

public interface ForkSnapshotService {

    ForkSnapshotRecord save(UUID applicationId, ScoreRequest request, ScoreResponse score,
                            OffsetDateTime calculatedAt);
}

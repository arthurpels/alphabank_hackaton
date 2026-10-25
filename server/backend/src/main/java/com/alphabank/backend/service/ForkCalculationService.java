package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;

public interface ForkCalculationService {

    ForkCalculation calculate(ApplicationRecord application, ProductRecord product, String inn);

    ForkSnapshotRecord save(UUID applicationId, ScoreRequest request, ScoreResponse score);
}

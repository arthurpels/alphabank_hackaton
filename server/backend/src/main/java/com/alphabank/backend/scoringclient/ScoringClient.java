package com.alphabank.backend.scoringclient;

import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;

public interface ScoringClient {

    ScoreResponse score(ScoreRequest request);
}

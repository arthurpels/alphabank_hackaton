package com.alphabank.backend.scoringclient;

import com.alphabank.backend.scoringclient.dto.PurposeRequest;
import com.alphabank.backend.scoringclient.dto.PurposeResponse;

public interface PurposeClient {

    PurposeResponse classify(PurposeRequest request);
}

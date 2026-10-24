package com.alphabank.backend.scoringclient;

import com.alphabank.backend.scoringclient.dto.AnomaliesRequest;
import com.alphabank.backend.scoringclient.dto.AnomaliesResponse;

public interface AnomalyClient {

    AnomaliesResponse detect(AnomaliesRequest request);
}

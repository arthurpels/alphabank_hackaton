package com.alphabank.backend.scoringclient;

import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;

public interface SummaryClient {

    UnderwritingSummaryDto summarize(SummaryRequest request);
}

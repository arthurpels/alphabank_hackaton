package com.alphabank.backend.scoringclient;

import com.alphabank.backend.scoringclient.dto.OfferGridResponse;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;

public interface OfferGridClient {

    OfferGridResponse grid(ScoreRequest request);
}

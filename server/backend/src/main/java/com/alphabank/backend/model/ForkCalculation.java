package com.alphabank.backend.model;

import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;

public record ForkCalculation(EnrichmentResult enrichment, ScoreRequest request, ScoreResponse score) {
}

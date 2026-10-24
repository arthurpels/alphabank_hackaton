package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.enums.ProductCode;

public record ScoreResponse(
        String rulesVersion,
        ProductCode productCode,
        double rateFrom,
        double rateTo,
        Thresholds thresholds,
        List<Factor> factors,
        List<StopFactor> stopFactors,
        Policy policy,
        boolean passes) {
}

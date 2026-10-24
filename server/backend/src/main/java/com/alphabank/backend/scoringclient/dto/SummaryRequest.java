package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.dto.response.FactRefDto;
import com.alphabank.backend.enums.ProductCode;

public record SummaryRequest(
        ProductCode productCode,
        long amount,
        int termMonths,
        String goal,
        double rateFrom,
        double rateTo,
        double approveMax,
        List<SummarySpot> spots,
        List<FactRefDto> facts) {
}

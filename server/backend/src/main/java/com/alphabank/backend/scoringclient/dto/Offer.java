package com.alphabank.backend.scoringclient.dto;

import com.alphabank.backend.enums.ProductCode;

public record Offer(
        ProductCode productCode,
        long amount,
        int termMonths,
        String collateralType,
        double rateFrom,
        double rateTo,
        Policy policy) {
}

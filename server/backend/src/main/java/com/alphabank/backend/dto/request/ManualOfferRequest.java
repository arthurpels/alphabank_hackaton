package com.alphabank.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.alphabank.backend.enums.ProductCode;

public record ManualOfferRequest(
        @NotNull ProductCode productCode,
        @NotNull @Positive Long amount,
        @NotNull @Positive Integer termMonths,
        String collateralType) {
}

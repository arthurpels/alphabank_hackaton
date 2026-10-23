package com.alphabank.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.alphabank.backend.enums.ProductCode;

public record ApplicationUpdateRequest(
        ProductCode productCode,
        @Positive Long amount,
        @Positive Integer termMonths,
        @Size(max = 1000) String goal,
        @Valid ConsentsRequest consents) {
}

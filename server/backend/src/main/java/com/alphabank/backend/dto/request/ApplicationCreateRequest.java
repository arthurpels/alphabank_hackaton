package com.alphabank.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.alphabank.backend.enums.ProductCode;

public record ApplicationCreateRequest(
        @NotNull ProductCode productCode,
        @NotNull @Positive Long amount,
        @NotNull @Positive Integer termMonths,
        @NotBlank @Size(max = 1000) String goal,
        @NotNull @Valid ConsentsRequest consents) {
}

package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.ProductCode;

public record ApplicationSummaryDto(
        UUID id,
        ApplicationStatus status,
        ProductCode productCode,
        long amount,
        int termMonths,
        ForkDto fork,
        OffsetDateTime createdAt) {
}

package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.ProductCode;

public record QueueItemDto(
        UUID applicationId,
        String companyName,
        String inn,
        ProductCode productCode,
        long amount,
        int termMonths,
        ApplicationStatus status,
        ForkDto fork,
        int openSpots,
        int onReviewSpots,
        OffsetDateTime submittedAt) {
}

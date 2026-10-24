package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.ApplicationStatus;

public record ApplicationRecord(
        UUID id,
        UUID companyId,
        UUID userId,
        UUID productId,
        long amount,
        int termMonths,
        String goal,
        ApplicationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt,
        OffsetDateTime infoRequestedAt) {
}

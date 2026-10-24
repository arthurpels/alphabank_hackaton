package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.OfferStatus;

public record OfferRecord(
        UUID id,
        UUID applicationId,
        UUID productId,
        long amount,
        int termMonths,
        String collateralType,
        String collateralComment,
        UUID forkSnapshotId,
        DecidedBy proposedBy,
        OfferStatus status,
        String comment,
        OffsetDateTime createdAt,
        OffsetDateTime chosenAt) {
}

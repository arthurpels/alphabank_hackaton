package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.ProductCode;

public record OfferDto(
        UUID id,
        ProductCode productCode,
        long amount,
        int termMonths,
        String collateralType,
        String collateralComment,
        ForkDto fork,
        DecidedBy proposedBy,
        OfferStatus status,
        boolean isRequested,
        boolean isManual,
        String comment,
        OffsetDateTime chosenAt) {
}

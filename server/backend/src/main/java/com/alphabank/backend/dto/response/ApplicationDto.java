package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;

public record ApplicationDto(
        UUID id,
        ApplicationStatus status,
        CompanyDto company,
        ProductDto product,
        long amount,
        int termMonths,
        String goal,
        ConsentsDto consents,
        List<ApplicationAction> availableActions,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt,
        OffsetDateTime infoRequestedAt,
        OffsetDateTime infoRequestDeadline) {
}

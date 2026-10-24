package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.AnswerOptionDto;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;

public record SpotRecord(
        UUID id,
        UUID applicationId,
        SpotType type,
        String title,
        String clientHint,
        String scoringFactor,
        Double pricePp,
        CloseMethod closeMethod,
        SpotStatus status,
        DecidedBy addedBy,
        SourceCode source,
        Boolean removable,
        List<AnswerOptionDto> answerOptions,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt) {
}

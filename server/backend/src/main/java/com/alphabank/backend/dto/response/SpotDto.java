package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;

public record SpotDto(
        UUID id,
        SpotType type,
        String title,
        String clientHint,
        String scoringFactor,
        double pricePp,
        CloseMethod closeMethod,
        SpotStatus status,
        DecidedBy addedBy,
        SourceCode source,
        Boolean removable,
        List<AnswerOptionDto> answerOptions,
        SpotAnswerDto lastAnswer,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt) {
}

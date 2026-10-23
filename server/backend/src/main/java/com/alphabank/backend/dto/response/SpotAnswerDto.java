package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.AnswerVerdict;

public record SpotAnswerDto(
        UUID id,
        String optionCode,
        String text,
        UUID documentId,
        AnswerVerdict verdict,
        String reviewedBy,
        String reviewComment,
        OffsetDateTime createdAt,
        OffsetDateTime reviewedAt) {
}

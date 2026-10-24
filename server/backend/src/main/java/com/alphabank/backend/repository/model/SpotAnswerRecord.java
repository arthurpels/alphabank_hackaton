package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.AnswerVerdict;

public record SpotAnswerRecord(
        UUID id,
        UUID spotId,
        String optionCode,
        String text,
        UUID documentId,
        AnswerVerdict verdict,
        String reviewedBy,
        String reviewComment,
        OffsetDateTime createdAt,
        OffsetDateTime reviewedAt) {
}

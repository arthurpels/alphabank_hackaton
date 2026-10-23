package com.alphabank.backend.dto.request;

import jakarta.validation.constraints.NotNull;

import com.alphabank.backend.enums.AnswerVerdict;

public record SpotReviewRequest(
        @NotNull AnswerVerdict verdict,
        String comment) {
}

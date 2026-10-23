package com.alphabank.backend.dto.request;

import jakarta.validation.constraints.NotNull;

public record ConsentsRequest(
        @NotNull Boolean bki,
        @NotNull Boolean personalData) {
}

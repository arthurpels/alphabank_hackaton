package com.alphabank.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SpotType;

public record SpotCreateRequest(
        @NotNull SpotType type,
        @NotBlank String title,
        @NotBlank String clientHint,
        @NotNull CloseMethod closeMethod) {
}

package com.alphabank.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SourceCode;

public record SpotCloseRequest(
        @NotNull CloseMethod method,
        SourceCode sourceCode,
        Boolean consent,
        String optionCode,
        @Size(max = 2000) String text,
        UUID documentId,
        String collateralType,
        String collateralComment,
        @Positive Long contractAmount,
        @Positive Integer contractTermMonths) {
}

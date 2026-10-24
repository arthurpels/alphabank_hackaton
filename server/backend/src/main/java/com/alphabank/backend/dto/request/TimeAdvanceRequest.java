package com.alphabank.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TimeAdvanceRequest(@NotNull @Min(1) @Max(30) Integer days) {
}

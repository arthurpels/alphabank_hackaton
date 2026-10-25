package com.alphabank.backend.dto.response;

import java.util.UUID;

public record UsedVerdictDto(UUID spotId, String scoringFactor, PurposeVerdictDto verdict) {
}

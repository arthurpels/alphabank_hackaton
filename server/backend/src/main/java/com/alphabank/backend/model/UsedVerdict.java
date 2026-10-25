package com.alphabank.backend.model;

import java.util.UUID;

import com.alphabank.backend.dto.response.PurposeVerdictDto;

public record UsedVerdict(UUID spotId, String scoringFactor, PurposeVerdictDto verdict) {
}

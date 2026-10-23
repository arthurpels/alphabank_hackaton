package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.DecisionZone;

public record ForkDto(
        UUID id,
        double rateFrom,
        double rateTo,
        DecisionZone zone,
        ZoneThresholdsDto thresholds,
        String rulesVersion,
        OffsetDateTime calculatedAt) {
}

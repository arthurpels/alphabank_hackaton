package com.alphabank.backend.scoringclient.dto;

import java.util.UUID;

import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;

public record SummarySpot(
        UUID spotId,
        SpotType type,
        String title,
        SpotStatus status,
        Double pricePp,
        String answer) {
}

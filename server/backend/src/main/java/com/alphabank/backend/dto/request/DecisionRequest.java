package com.alphabank.backend.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.alphabank.backend.enums.DecisionType;

public record DecisionRequest(
        @NotNull DecisionType type,
        @Size(min = 1, max = 3) List<UUID> offerIds,
        @Valid ManualOfferRequest manualOffer,
        boolean override,
        String comment,
        UUID reasonSpotId) {
}

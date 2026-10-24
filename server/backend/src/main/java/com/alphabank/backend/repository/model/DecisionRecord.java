package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.DecisionType;

public record DecisionRecord(
        UUID id,
        UUID applicationId,
        DecisionType type,
        UUID offerId,
        DecidedBy decidedBy,
        UUID decidedByUserId,
        String rulesVersion,
        boolean override,
        String comment,
        UUID reasonSpotId,
        OffsetDateTime decidedAt) {
}

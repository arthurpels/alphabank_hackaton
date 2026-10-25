package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.DecisionType;

public record DecisionDto(
        UUID id,
        DecisionType type,
        DecidedBy decidedBy,
        UUID decidedByUserId,
        String rulesVersion,
        boolean override,
        String comment,
        UUID reasonSpotId,
        OffsetDateTime decidedAt,
        UUID forkSnapshotId,
        String requestHash) {
}

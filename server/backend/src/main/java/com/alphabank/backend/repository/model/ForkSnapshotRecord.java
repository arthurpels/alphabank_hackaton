package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ForkSnapshotRecord(
        UUID id,
        UUID applicationId,
        double rateFrom,
        double rateTo,
        Double approveMax,
        Double rejectMin,
        String rulesVersion,
        OffsetDateTime calculatedAt) {
}

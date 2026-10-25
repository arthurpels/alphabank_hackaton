package com.alphabank.backend.repository.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.model.SnapshotTrace;

public record ForkSnapshotRecord(
        UUID id,
        UUID applicationId,
        double rateFrom,
        double rateTo,
        Double approveMax,
        Double rejectMin,
        String rulesVersion,
        LocalDate asOf,
        OffsetDateTime calculatedAt,
        SnapshotTrace trace) {
}

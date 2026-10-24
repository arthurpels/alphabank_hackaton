package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.alphabank.backend.enums.SourceCode;

public record ApplicationSourceRecord(
        UUID id,
        UUID applicationId,
        SourceCode sourceCode,
        boolean consentGiven,
        OffsetDateTime connectedAt) {
}

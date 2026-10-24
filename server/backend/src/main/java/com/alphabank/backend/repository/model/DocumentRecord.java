package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentRecord(
        UUID id,
        UUID applicationId,
        String kind,
        String filePath,
        String originalName,
        String contentType,
        OffsetDateTime uploadedAt) {
}

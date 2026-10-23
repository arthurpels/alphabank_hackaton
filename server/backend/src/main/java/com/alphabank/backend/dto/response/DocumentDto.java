package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        String kind,
        String fileName,
        OffsetDateTime uploadedAt) {
}

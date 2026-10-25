package com.alphabank.backend.repository.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.LlmMetaDto;
import com.alphabank.backend.dto.response.SummaryItemDto;
import com.alphabank.backend.enums.SummaryStatus;

public record UnderwritingSummaryRecord(
        UUID applicationId,
        SummaryStatus status,
        String generatedBy,
        List<SummaryItemDto> items,
        LlmMetaDto llm,
        OffsetDateTime generatedAt,
        OffsetDateTime updatedAt,
        long revision) {
}

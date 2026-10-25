package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.alphabank.backend.enums.SummaryStatus;

public record UnderwritingSummaryResponseDto(
        SummaryStatus status,
        String generatedBy,
        List<SummaryItemDto> items,
        LlmMetaDto llm,
        OffsetDateTime generatedAt) {
}

package com.alphabank.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.dto.response.LlmMetaDto;
import com.alphabank.backend.dto.response.SummaryItemDto;
import com.alphabank.backend.enums.SummaryStatus;
import com.alphabank.backend.repository.model.UnderwritingSummaryRecord;

public interface UnderwritingSummaryRepository {

    Optional<UnderwritingSummaryRecord> findByApplicationId(UUID applicationId);

    long saveTemplate(UUID applicationId, SummaryStatus status, List<SummaryItemDto> items, OffsetDateTime now);

    void complete(UUID applicationId, long revision, String generatedBy, List<SummaryItemDto> items,
                  LlmMetaDto llm, OffsetDateTime now);

    void markReady(UUID applicationId, long revision, OffsetDateTime now);
}

package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ForkVerificationDto(
        UUID snapshotId,
        boolean reproducible,
        String rulesVersion,
        String engineVersion,
        String currentEngineVersion,
        String requestHash,
        OffsetDateTime calculatedAt,
        List<UsedVerdictDto> verdicts,
        List<FieldDiffDto> diff) {
}

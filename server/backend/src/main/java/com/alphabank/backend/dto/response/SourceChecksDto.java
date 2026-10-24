package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record SourceChecksDto(
        OffsetDateTime checkedAt,
        List<SourceCheckDto> items) {
}

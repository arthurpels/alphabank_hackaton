package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DemoTimeDto(
        OffsetDateTime now,
        long shiftedDays,
        List<UUID> cancelledApplicationIds) {
}

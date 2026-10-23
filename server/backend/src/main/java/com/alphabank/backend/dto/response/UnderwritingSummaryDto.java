package com.alphabank.backend.dto.response;

import java.util.List;

public record UnderwritingSummaryDto(String generatedBy, List<SummaryItemDto> items) {
}

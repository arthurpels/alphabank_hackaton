package com.alphabank.backend.dto.response;

import java.util.List;

public record SummaryItemDto(String text, List<FactRefDto> facts) {
}

package com.alphabank.backend.dto.response;

import com.alphabank.backend.enums.SourceCode;

public record AnomalyRefDto(String code, String month, int changePct, SourceCode source) {
}

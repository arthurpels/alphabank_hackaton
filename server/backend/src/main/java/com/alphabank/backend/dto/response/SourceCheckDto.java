package com.alphabank.backend.dto.response;

import java.util.List;

import com.alphabank.backend.enums.CheckStatus;
import com.alphabank.backend.enums.SourceCode;

public record SourceCheckDto(
        SourceCode source,
        CheckStatus status,
        List<String> facts,
        List<String> problems) {
}

package com.alphabank.backend.dto.response;

import java.util.List;

import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;

public record PurposeVerdictDto(
        ProductCode productCode,
        PurposeStatus status,
        Purpose purpose,
        String generatedBy,
        List<String> matchedKeywords,
        String reason,
        LlmMetaDto llm,
        ProfileCheckDto profile) {
}

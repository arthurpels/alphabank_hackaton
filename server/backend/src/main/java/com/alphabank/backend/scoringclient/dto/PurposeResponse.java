package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.PurposeStatus;

public record PurposeResponse(
        PurposeStatus status,
        Purpose purpose,
        boolean applicable,
        String generatedBy,
        List<String> matchedKeywords,
        String reason,
        LlmMeta llm,
        ProfileCheck profile) {
}

package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.scoringclient.enums.Purpose;

public record PurposeResponse(
        Purpose purpose,
        boolean applicable,
        String generatedBy,
        List<String> matchedKeywords,
        String reason) {
}

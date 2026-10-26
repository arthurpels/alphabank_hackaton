package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.scoringclient.enums.AnswerClassifyStatus;
import com.alphabank.backend.scoringclient.enums.AnswerCode;

public record AnswerClassifyResponse(
        AnswerClassifyStatus status,
        AnswerCode code,
        String generatedBy,
        List<String> matchedKeywords,
        String reason,
        String evidence,
        String violation,
        LlmMeta llm) {
}

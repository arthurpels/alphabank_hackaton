package com.alphabank.backend.dto.response;

import java.util.List;

import com.alphabank.backend.scoringclient.enums.AnswerClassifyStatus;
import com.alphabank.backend.scoringclient.enums.AnswerCode;

public record AnswerClassificationDto(
        AnswerClassifyStatus status,
        AnswerCode code,
        String generatedBy,
        List<String> matchedKeywords,
        String reason,
        String evidence,
        String violation,
        LlmMetaDto llm) {
}

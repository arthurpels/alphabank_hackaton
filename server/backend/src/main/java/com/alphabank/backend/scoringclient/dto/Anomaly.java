package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.dto.response.AnswerOptionDto;
import com.alphabank.backend.enums.SourceCode;

public record Anomaly(
        String code,
        SourceCode source,
        String month,
        int changePct,
        String title,
        String clientHint,
        List<AnswerOptionDto> answerOptions) {
}

package com.alphabank.backend.dto.response;

import com.alphabank.backend.scoringclient.enums.AnswerCode;

public record AnomalyVerdictDto(
        AnomalyRefDto anomaly,
        AnswerCode code,
        AnswerClassificationDto classification,
        AnswerCheckDto check) {
}

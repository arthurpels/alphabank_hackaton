package com.alphabank.backend.dto.response;

import com.alphabank.backend.scoringclient.enums.AnswerCheckVerdict;

public record AnswerCheckDto(AnswerCheckVerdict verdict, String rule, String explanation) {
}

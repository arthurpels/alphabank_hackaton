package com.alphabank.backend.scoringclient.dto;

import com.alphabank.backend.scoringclient.enums.AnswerCheckVerdict;

public record AnswerVerifyResponse(AnswerCheckVerdict verdict, String rule, String explanation) {
}

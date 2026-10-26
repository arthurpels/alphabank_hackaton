package com.alphabank.backend.scoringclient.dto;

import java.util.List;

import com.alphabank.backend.scoringclient.enums.AnswerCode;

public record AnswerVerifyRequest(AnomalyRef anomaly, AnswerCode answerCode, List<Series> series) {
}

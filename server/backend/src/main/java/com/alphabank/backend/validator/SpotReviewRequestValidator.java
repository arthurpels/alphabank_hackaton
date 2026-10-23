package com.alphabank.backend.validator;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.SpotReviewRequest;
import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.exception.ApiException;

@Component
public class SpotReviewRequestValidator {

    public void validate(SpotReviewRequest request) {
        if (request.verdict() == AnswerVerdict.PENDING) {
            throw ApiException.validation("verdict — ACCEPTED или REJECTED");
        }
        if (request.verdict() == AnswerVerdict.REJECTED
                && (request.comment() == null || request.comment().isBlank())) {
            throw ApiException.validation("При REJECTED нужен comment — его увидит клиент");
        }
    }
}

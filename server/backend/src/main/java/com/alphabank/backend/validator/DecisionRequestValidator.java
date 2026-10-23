package com.alphabank.backend.validator;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.DecisionRequest;
import com.alphabank.backend.exception.ApiException;

@Component
public class DecisionRequestValidator {

    public void validate(DecisionRequest request) {
        boolean hasOffers = request.offerIds() != null && !request.offerIds().isEmpty();
        boolean hasComment = request.comment() != null && !request.comment().isBlank();
        switch (request.type()) {
            case ALTERNATIVE -> {
                if (!hasOffers && request.manualOffer() == null) {
                    throw ApiException.validation("Для ALTERNATIVE нужен хотя бы один offerIds или manualOffer");
                }
                if (request.manualOffer() != null && (!request.override() || !hasComment)) {
                    throw ApiException.validation("Ручной вариант (manualOffer) — только с override = true и comment");
                }
            }
            case REJECTED -> {
                if (request.reasonSpotId() == null || !hasComment) {
                    throw ApiException.validation("Отказ — только с причиной: нужны reasonSpotId и comment");
                }
            }
            case APPROVED -> {
                if (hasOffers || request.manualOffer() != null) {
                    throw ApiException.validation(
                            "APPROVED одобряет запрошенные условия — варианты передаются только для ALTERNATIVE");
                }
            }
        }
        if (request.override() && !hasComment) {
            throw ApiException.validation("При override = true нужен comment");
        }
    }
}

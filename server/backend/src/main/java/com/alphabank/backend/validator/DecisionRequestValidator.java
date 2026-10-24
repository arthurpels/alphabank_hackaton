package com.alphabank.backend.validator;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.DecisionRequest;
import com.alphabank.backend.exception.ApiException;

@Component
public class DecisionRequestValidator {

    private static final int MAX_OFFERS = 3;
    private static final Set<String> COLLATERAL_TYPES = Set.of("GUARANTEE", "PLEDGE");

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
                int total = (hasOffers ? request.offerIds().size() : 0) + (request.manualOffer() == null ? 0 : 1);
                if (total > MAX_OFFERS) {
                    throw ApiException.validation("Клиенту показываем не больше " + MAX_OFFERS + " вариантов");
                }
                String collateral = request.manualOffer() == null ? null : request.manualOffer().collateralType();
                if (collateral != null && !COLLATERAL_TYPES.contains(collateral)) {
                    throw ApiException.validation("collateralType ручного варианта — один из " + COLLATERAL_TYPES);
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

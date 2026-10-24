package com.alphabank.backend.validator;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.exception.ApiException;

@Component
public class SpotCloseRequestValidator {

    public void validate(SpotCloseRequest request) {
        String missing = switch (request.method()) {
            case CONNECT_SOURCE -> request.sourceCode() == null ? "sourceCode"
                    : !Boolean.TRUE.equals(request.consent()) ? "consent: true" : null;
            case ANSWER -> isBlank(request.optionCode()) && isBlank(request.text()) ? "optionCode или text" : null;
            case UPLOAD -> request.documentId() == null ? "documentId" : null;
            case PAY -> null;
            case ADD_COLLATERAL -> isBlank(request.collateralType()) ? "collateralType" : null;
        };
        if (missing != null) {
            throw ApiException.validation("Для способа " + request.method() + " нужно: " + missing);
        }
        if ((request.contractAmount() == null) != (request.contractTermMonths() == null)) {
            throw ApiException.validation("Сумма и срок договора передаются вместе: contractAmount и contractTermMonths");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

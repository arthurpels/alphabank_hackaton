package com.alphabank.backend.validator;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.request.SpotCreateRequest;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.service.impl.FactorStateServiceImpl;

@Component
public class SpotCreateRequestValidator {

    private static final Set<String> FACTORS = Set.of(FactorStateServiceImpl.DEBT_LOAD,
            FactorStateServiceImpl.REVENUE_STABILITY, FactorStateServiceImpl.PURPOSE,
            FactorStateServiceImpl.COLLATERAL);

    public void validate(SpotCreateRequest request) {
        if (request.type() == SpotType.STOP_FACTOR) {
            throw ApiException.validation("Стоп-факторы приходят только из источников — андеррайтер их не добавляет");
        }
        if (request.scoringFactor() != null && !FACTORS.contains(request.scoringFactor())) {
            throw ApiException.validation("scoringFactor — один из " + FACTORS);
        }
    }
}

package com.alphabank.backend.scoringclient.dto;

import com.alphabank.backend.scoringclient.enums.FactorCode;

public record Factor(
        FactorCode code,
        String title,
        boolean applicable,
        boolean known,
        Double value,
        Double best,
        Double worst,
        Double spotPricePp) {
}

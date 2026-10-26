package com.alphabank.backend.dto.response;

import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.scoringclient.enums.FactorCode;

public record ForkFactorDto(
        FactorCode code,
        String title,
        boolean applicable,
        boolean known,
        Double value,
        Double best,
        Double worst,
        Double spotPricePp,
        SourceCode source) {
}

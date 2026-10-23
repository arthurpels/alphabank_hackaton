package com.alphabank.backend.dto.response;

import java.util.List;

public record ApplicationDetailsDto(
        ApplicationDto application,
        ForkDto fork,
        List<SpotDto> spots,
        List<OfferDto> offers,
        DecisionDto decision,
        ContractDto contract) {
}

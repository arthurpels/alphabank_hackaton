package com.alphabank.backend.enrichment.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcquiringResponse(
        String source,
        String inn,
        List<MonthTurnover> months,
        int avgTicket) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MonthTurnover(
            String month,
            long turnover,
            int txCount) {
    }
}

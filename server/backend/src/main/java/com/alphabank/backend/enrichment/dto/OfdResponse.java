package com.alphabank.backend.enrichment.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OfdResponse(
        String source,
        String inn,
        List<MonthRevenue> months,
        double shareOfTotalRevenue) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MonthRevenue(
            String month,
            long revenue) {
    }
}

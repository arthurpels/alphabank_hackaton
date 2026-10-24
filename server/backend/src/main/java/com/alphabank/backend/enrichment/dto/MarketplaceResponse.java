package com.alphabank.backend.enrichment.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MarketplaceResponse(
        String source,
        String inn,
        List<MonthSales> months,
        double rating,
        int storeAgeMonths) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MonthSales(
            String month,
            long gmv,
            long payout,
            int returnsPct) {
    }
}

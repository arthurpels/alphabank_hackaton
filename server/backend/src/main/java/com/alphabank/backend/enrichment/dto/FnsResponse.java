package com.alphabank.backend.enrichment.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FnsResponse(
        String source,
        String inn,
        TaxDebt taxDebt,
        int unfiledReports,
        String taxSystem) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaxDebt(
            long amount,
            String currency,
            LocalDate asOf,
            LocalDate paidAt) {
    }
}

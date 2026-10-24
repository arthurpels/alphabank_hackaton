package com.alphabank.backend.enrichment.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BkiResponse(
        String source,
        String inn,
        int creditScore,
        List<Credit> openCredits,
        int closedCredits,
        Delinquencies delinquencies,
        @JsonProperty("inquiries_6m") int inquiries6m) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Credit(
            String type,
            String bank,
            long limit,
            long debt,
            LocalDate openedAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Delinquencies(
            int current,
            int maxDaysEver,
            @JsonProperty("last_12m") int last12m) {
    }
}

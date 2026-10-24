package com.alphabank.backend.enrichment.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EgrulResponse(
        String source,
        String inn,
        String name,
        String legalForm,
        String status,
        LocalDate registeredAt,
        Director director,
        Address address,
        String okvedMain,
        int foundersCount) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Director(
            String fio,
            LocalDate appointedAt,
            @JsonProperty("is_mass") boolean isMass) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Address(
            String value,
            @JsonProperty("is_mass") boolean isMass) {
    }
}

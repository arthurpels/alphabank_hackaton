package com.alphabank.backend.scoringclient.dto;

public record Policy(
        String metric,
        Double value,
        Double cap,
        boolean amountInRange,
        boolean ok) {
}

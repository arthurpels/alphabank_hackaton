package com.alphabank.backend.scoringclient.dto;

public record Policy(
        String metric,
        Double value,
        Double cap,
        long existingPayment,
        boolean amountInRange,
        boolean ok) {
}

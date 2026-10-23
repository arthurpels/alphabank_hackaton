package com.alphabank.backend.dto.response;

import java.time.LocalDate;

public record PaymentScheduleItemDto(
        int number,
        LocalDate date,
        long payment,
        long principal,
        long interest,
        long balance) {
}

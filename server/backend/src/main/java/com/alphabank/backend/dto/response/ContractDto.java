package com.alphabank.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ContractDto(
        UUID offerId,
        long amount,
        int termMonths,
        double rate,
        long monthlyPayment,
        OffsetDateTime signedAt,
        OffsetDateTime issuedAt,
        List<PaymentScheduleItemDto> schedule) {
}

package com.alphabank.backend.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.alphabank.backend.dto.response.PaymentScheduleItemDto;

public final class AnnuityScheduleCalculator {

    private AnnuityScheduleCalculator() {
    }

    public static List<PaymentScheduleItemDto> calculate(long amount, double ratePercent, int months,
                                                         LocalDate firstPaymentDate) {
        double monthlyRate = ratePercent / 100 / 12;
        long payment = Math.round(amount * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months)));
        List<PaymentScheduleItemDto> items = new ArrayList<>();
        long balance = amount;
        for (int i = 1; i <= months; i++) {
            long interest = Math.round(balance * monthlyRate);
            long principal = i == months ? balance : payment - interest;
            balance -= principal;
            items.add(new PaymentScheduleItemDto(i, firstPaymentDate.plusMonths(i - 1L), principal + interest,
                    principal, interest, balance));
        }
        return items;
    }
}

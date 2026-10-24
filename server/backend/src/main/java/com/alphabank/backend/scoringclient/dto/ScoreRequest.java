package com.alphabank.backend.scoringclient.dto;

import java.time.LocalDate;

import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.scoringclient.enums.Collateral;
import com.alphabank.backend.scoringclient.enums.Purpose;
import com.alphabank.backend.scoringclient.enums.Stability;

public record ScoreRequest(
        LocalDate asOf,
        ProductCode productCode,
        long amount,
        int termMonths,
        String egrulStatus,
        LocalDate registeredAt,
        LocalDate directorSince,
        boolean massAddress,
        Integer creditScore,
        int delinq12m,
        int bkiCurrentOverdue,
        long taxDebt,
        int unfiledReports,
        Long monthlyRevenue,
        Stability stability,
        Purpose purpose,
        Collateral collateral) {
}

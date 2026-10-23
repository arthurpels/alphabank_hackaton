package com.alphabank.backend.stub;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.AnswerOptionDto;
import com.alphabank.backend.dto.response.SpotAnswerDto;
import com.alphabank.backend.dto.response.SpotDto;
import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;

public final class StubSpots {

    private StubSpots() {
    }

    public static List<SpotDto> forStatus(ApplicationStatus status) {
        if (status == ApplicationStatus.DRAFT) {
            return List.of();
        }
        boolean collecting = status == ApplicationStatus.COLLECTING || status == ApplicationStatus.CANCELLED;
        boolean inReview = status == ApplicationStatus.UNDERWRITING || status == ApplicationStatus.INFO_REQUESTED
                || status == ApplicationStatus.REJECTED;
        SpotStatus answered = collecting ? SpotStatus.OPEN : SpotStatus.CLOSED;

        List<SpotDto> spots = new ArrayList<>();
        spots.add(spot(StubIds.SPOT_OFD, SpotType.NO_DATA, "Продажи через кассу (~70% выручки)",
                "Видим только деньги на счёте. Продажи через кассу пока считаем нулём.",
                "revenue_coverage", 3.5, CloseMethod.CONNECT_SOURCE, SourceCode.OFD, List.of(), answered, null));
        spots.add(spot(StubIds.SPOT_MARCH, SpotType.ANOMALY, "В марте выручка упала на 45%",
                "Пока нет объяснения — считаем это риском. Что случилось?",
                "revenue_stability", 2.5, CloseMethod.ANSWER, SourceCode.OFD, marchOptions(), answered,
                collecting ? null : marchAnswer()));
        spots.add(spot(StubIds.SPOT_GOAL, SpotType.NO_DATA, "На что именно нужны деньги",
                "Опишите своими словами — подберём продукт и учтём цель.",
                "loan_purpose", 1.5, CloseMethod.ANSWER, null, List.of(),
                collecting || inReview ? SpotStatus.OPEN : SpotStatus.CLOSED, null));
        spots.add(spot(StubIds.SPOT_COLLATERAL, SpotType.COLLATERAL, "Залог или поручительство",
                "Самый дорогой для вас способ — оставьте на конец.",
                "collateral", 1.5, CloseMethod.ADD_COLLATERAL, null, List.of(), SpotStatus.OPEN, null));
        if (status == ApplicationStatus.INFO_REQUESTED) {
            spots.add(underwriterRequest());
        }
        return spots;
    }

    private static SpotDto spot(UUID id, SpotType type, String title, String hint, String factor, double price,
                                CloseMethod method, SourceCode source, List<AnswerOptionDto> options,
                                SpotStatus status, SpotAnswerDto answer) {
        return new SpotDto(id, type, title, hint, factor, price, method, status, DecidedBy.SYSTEM, source,
                type == SpotType.STOP_FACTOR ? Boolean.TRUE : null, options, answer, StubIds.T0.plusSeconds(40),
                status == SpotStatus.CLOSED ? StubIds.T0.plusMinutes(2) : null);
    }

    private static SpotDto underwriterRequest() {
        return new SpotDto(StubIds.SPOT_UNDERWRITER, SpotType.DOCUMENT, "Договор подряда на ремонт",
                "Подтвердите ремонт в марте. Нет договора — подойдёт акт выполненных работ.",
                null, 0, CloseMethod.UPLOAD, SpotStatus.OPEN, DecidedBy.UNDERWRITER, null, null,
                List.of(), null, StubIds.T0.plusHours(1), null);
    }

    private static List<AnswerOptionDto> marchOptions() {
        return List.of(new AnswerOptionDto("RENOVATION", "Был ремонт"),
                new AnswerOptionDto("SEASONAL", "Сезонный спад"),
                new AnswerOptionDto("CLIENT_LOST", "Ушёл крупный клиент"));
    }

    private static SpotAnswerDto marchAnswer() {
        return new SpotAnswerDto(StubIds.id("e001"), "RENOVATION", "Был ремонт зала", null,
                AnswerVerdict.ACCEPTED, "SYSTEM", null, StubIds.T0.plusMinutes(3), StubIds.T0.plusMinutes(3));
    }
}

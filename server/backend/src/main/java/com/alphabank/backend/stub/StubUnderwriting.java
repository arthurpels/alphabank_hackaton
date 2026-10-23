package com.alphabank.backend.stub;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.dto.response.FactRefDto;
import com.alphabank.backend.dto.response.QueueItemDto;
import com.alphabank.backend.dto.response.SummaryItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.ProductCode;

public final class StubUnderwriting {

    private StubUnderwriting() {
    }

    public static List<QueueItemDto> queue() {
        return List.of(new QueueItemDto(StubIds.COFFEE_APPLICATION_ID, "ООО «Кофейня»", "7701234560",
                ProductCode.EQUIPMENT, StubOffers.COFFEE_AMOUNT, StubOffers.COFFEE_TERM,
                ApplicationStatus.UNDERWRITING, StubForks.afterAnomalyAnswer(), 2, 0, StubIds.T0.plusMinutes(6)));
    }

    public static UnderwritingSummaryDto summary() {
        return new UnderwritingSummaryDto("TEMPLATE", List.of(
                new SummaryItemDto("Выручка в марте упала на 45 %, клиент объясняет ремонтом и приложил договор подряда.",
                        List.of(new FactRefDto("OFD", "выручка 2026-03 — 830 000 ₽", StubIds.SPOT_MARCH))),
                new SummaryItemDto("Кредитная история хорошая: скор 712, просрочек за 12 месяцев нет.",
                        List.of(new FactRefDto("BKI", "credit_score — 712, просрочки за 12 мес. — 0", null))),
                new SummaryItemDto("Обеспечение не предложено: с поручительством вилка сузилась бы ещё на 1,5 п.п.",
                        List.of(new FactRefDto("SCORING", "цена пятна «Залог» — 1,5 п.п.", StubIds.SPOT_COLLATERAL)))));
    }

    public static List<AuditEventDto> history() {
        return List.of(
                event("1001", AuditEventType.STATUS_CHANGE, null, "DRAFT", StubIds.COFFEE_USER_ID, null, 0),
                event("1002", AuditEventType.STATUS_CHANGE, "DRAFT", "COLLECTING", StubIds.COFFEE_USER_ID, "Рассчитать", 1),
                event("1003", AuditEventType.SPOT_CREATED, null, null, null, "Найдено 4 пятна", 1),
                event("1004", AuditEventType.SPOT_CLOSED, null, null, StubIds.COFFEE_USER_ID, "Подключён источник OFD", 2),
                event("1005", AuditEventType.SPOT_CREATED, null, null, null, "Почему в марте выручка упала на 45 %", 2),
                event("1006", AuditEventType.SPOT_REVIEWED, null, null, null, "Ответ «Был ремонт» принят", 3),
                event("1007", AuditEventType.SPOT_CLOSED, null, null, StubIds.COFFEE_USER_ID, "Цель: 2 кофемашины и витрина", 4),
                event("1008", AuditEventType.STATUS_CHANGE, "COLLECTING", "SUBMITTED", StubIds.COFFEE_USER_ID, null, 5),
                event("1009", AuditEventType.STATUS_CHANGE, "SUBMITTED", "AUTO_CHECK", null, null, 5),
                event("1010", AuditEventType.DECISION, "AUTO_CHECK", "APPROVED", null, "Правый край 19,5 % ≤ 20,0 %", 5));
    }

    private static AuditEventDto event(String idSuffix, AuditEventType type, String from, String to, UUID user,
                                       String reason, int minutes) {
        return new AuditEventDto(StubIds.id(idSuffix), type, from, to, user, reason, null,
                StubIds.T0.plusMinutes(minutes));
    }
}

package com.alphabank.backend.stub;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.ApplicationDto;
import com.alphabank.backend.dto.response.ApplicationSummaryDto;
import com.alphabank.backend.dto.response.ConsentsDto;
import com.alphabank.backend.dto.response.ContractDto;
import com.alphabank.backend.dto.response.DecisionDto;
import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.dto.response.PaymentScheduleItemDto;
import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.DecisionType;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.util.AnnuityScheduleCalculator;

public final class StubApplications {

    private StubApplications() {
    }

    public static ApplicationDetailsDto coffee(UUID id, ApplicationStatus status, UserRole role) {
        boolean infoRequested = status == ApplicationStatus.INFO_REQUESTED;
        ApplicationDto application = new ApplicationDto(id, status, StubUsers.coffeeCompany(),
                StubCatalog.product(ProductCode.EQUIPMENT), StubOffers.COFFEE_AMOUNT, StubOffers.COFFEE_TERM,
                "две кофемашины и витрина", new ConsentsDto(true, true), availableActions(status, role),
                StubIds.T0, isSubmitted(status) ? StubIds.T0.plusMinutes(4) : null,
                infoRequested ? StubIds.T0.plusHours(1) : null,
                infoRequested ? StubIds.T0.plusHours(1).plusDays(5) : null);
        return new ApplicationDetailsDto(application, fork(status), StubSpots.forStatus(status),
                StubOffers.forStatus(status, role), decision(status), contract(status));
    }

    public static ApplicationSummaryDto coffeeSummary() {
        return new ApplicationSummaryDto(StubIds.COFFEE_APPLICATION_ID, ApplicationStatus.COLLECTING,
                ProductCode.EQUIPMENT, StubOffers.COFFEE_AMOUNT, StubOffers.COFFEE_TERM, StubForks.wide(), StubIds.T0);
    }

    private static List<ApplicationAction> availableActions(ApplicationStatus status, UserRole role) {
        if (role == UserRole.UNDERWRITER) {
            return status == ApplicationStatus.UNDERWRITING
                    ? List.of(ApplicationAction.REVIEW_SPOT, ApplicationAction.ADD_SPOT, ApplicationAction.DECIDE)
                    : List.of();
        }
        return switch (status) {
            case DRAFT -> List.of(ApplicationAction.EDIT, ApplicationAction.EVALUATE, ApplicationAction.CANCEL);
            case COLLECTING -> List.of(ApplicationAction.EDIT, ApplicationAction.CLOSE_SPOT,
                    ApplicationAction.SUBMIT, ApplicationAction.CANCEL);
            case INFO_REQUESTED -> List.of(ApplicationAction.CLOSE_SPOT, ApplicationAction.CANCEL);
            case APPROVED, COUNTER_OFFER -> List.of(ApplicationAction.ACCEPT, ApplicationAction.CANCEL);
            case SIGNING -> List.of(ApplicationAction.SIGN);
            default -> List.of();
        };
    }

    private static boolean isSubmitted(ApplicationStatus status) {
        return status != ApplicationStatus.DRAFT && status != ApplicationStatus.COLLECTING
                && status != ApplicationStatus.CANCELLED;
    }

    private static ForkDto fork(ApplicationStatus status) {
        return switch (status) {
            case DRAFT -> null;
            case COLLECTING, CANCELLED -> StubForks.wide();
            case UNDERWRITING, INFO_REQUESTED, REJECTED -> StubForks.middle();
            default -> StubForks.narrow();
        };
    }

    private static DecisionDto decision(ApplicationStatus status) {
        return switch (status) {
            case APPROVED, SIGNING, ISSUED -> new DecisionDto(StubIds.id("f001"), DecisionType.APPROVED,
                    DecidedBy.SYSTEM, null, StubIds.RULES_VERSION, false, null, null, StubIds.T0.plusMinutes(4));
            case COUNTER_OFFER -> new DecisionDto(StubIds.id("f002"), DecisionType.ALTERNATIVE,
                    DecidedBy.UNDERWRITER, StubIds.UNDERWRITER_ID, StubIds.RULES_VERSION, false,
                    "Предлагаю дольше срок или меньше сумму", null, StubIds.T0.plusHours(2));
            case REJECTED -> new DecisionDto(StubIds.id("f003"), DecisionType.REJECTED,
                    DecidedBy.UNDERWRITER, StubIds.UNDERWRITER_ID, StubIds.RULES_VERSION, false,
                    "Падение выручки не подтверждено", StubIds.SPOT_MARCH, StubIds.T0.plusHours(2));
            default -> null;
        };
    }

    private static ContractDto contract(ApplicationStatus status) {
        if (status != ApplicationStatus.SIGNING && status != ApplicationStatus.ISSUED) {
            return null;
        }
        boolean issued = status == ApplicationStatus.ISSUED;
        double rate = StubForks.narrow().rateTo();
        List<PaymentScheduleItemDto> schedule = AnnuityScheduleCalculator.calculate(StubOffers.COFFEE_AMOUNT, rate,
                StubOffers.COFFEE_TERM, LocalDate.of(2026, 11, 23));
        return new ContractDto(StubIds.OFFER_REQUESTED, StubOffers.COFFEE_AMOUNT, StubOffers.COFFEE_TERM, rate,
                schedule.get(0).payment(), issued ? StubIds.T0.plusMinutes(15) : null,
                issued ? StubIds.T0.plusMinutes(15) : null, schedule);
    }
}

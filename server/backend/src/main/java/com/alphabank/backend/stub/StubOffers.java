package com.alphabank.backend.stub;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.UserRole;

public final class StubOffers {

    public static final long COFFEE_AMOUNT = 200_000_000L;
    public static final int COFFEE_TERM = 24;

    private StubOffers() {
    }

    public static List<OfferDto> forStatus(ApplicationStatus status, UserRole role) {
        return switch (status) {
            case UNDERWRITING -> role == UserRole.UNDERWRITER ? calculated() : List.of();
            case APPROVED -> List.of(requested(OfferStatus.OFFERED));
            case COUNTER_OFFER -> List.of(
                    offer(StubIds.OFFER_LONGER, COFFEE_AMOUNT, 36, StubForks.longerTerm(),
                            DecidedBy.UNDERWRITER, OfferStatus.OFFERED, false),
                    offer(StubIds.OFFER_SMALLER, 150_000_000L, COFFEE_TERM, StubForks.smallerAmount(),
                            DecidedBy.UNDERWRITER, OfferStatus.OFFERED, false));
            case SIGNING, ISSUED -> List.of(requested(OfferStatus.CHOSEN));
            default -> List.of();
        };
    }

    public static List<OfferDto> calculated() {
        return List.of(
                offer(StubIds.OFFER_LONGER, COFFEE_AMOUNT, 36, StubForks.longerTerm(),
                        DecidedBy.SYSTEM, OfferStatus.CALCULATED, false),
                offer(StubIds.OFFER_SMALLER, 150_000_000L, COFFEE_TERM, StubForks.smallerAmount(),
                        DecidedBy.SYSTEM, OfferStatus.CALCULATED, false));
    }

    private static OfferDto requested(OfferStatus status) {
        return offer(StubIds.OFFER_REQUESTED, COFFEE_AMOUNT, COFFEE_TERM, StubForks.narrow(), DecidedBy.SYSTEM,
                status, true);
    }

    private static OfferDto offer(UUID id, long amount, int term, ForkDto fork, DecidedBy by, OfferStatus status,
                                  boolean requested) {
        return new OfferDto(id, ProductCode.EQUIPMENT, amount, term, null, null, fork, by, status, requested, false,
                null, status == OfferStatus.CHOSEN ? StubIds.T0.plusMinutes(10) : null);
    }
}

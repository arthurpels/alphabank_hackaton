package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.OfferService;
import com.alphabank.backend.stub.StubOffers;

@Service
public class OfferServiceImpl implements OfferService {

    @Override
    public List<OfferDto> getOffers(CurrentUser user, UUID applicationId) {
        return user.isUnderwriter()
                ? StubOffers.calculated()
                : StubOffers.forStatus(ApplicationStatus.COUNTER_OFFER, user.role());
    }
}

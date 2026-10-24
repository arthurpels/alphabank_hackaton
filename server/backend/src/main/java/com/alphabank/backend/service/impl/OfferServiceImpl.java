package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.ApplicationDetailsService;
import com.alphabank.backend.service.OfferService;

@Service
public class OfferServiceImpl implements OfferService {

    private final ApplicationAccessService accessService;
    private final ApplicationDetailsService detailsService;

    public OfferServiceImpl(ApplicationAccessService accessService, ApplicationDetailsService detailsService) {
        this.accessService = accessService;
        this.detailsService = detailsService;
    }

    @Override
    public List<OfferDto> getOffers(CurrentUser user, UUID applicationId) {
        return detailsService.offers(accessService.load(user, applicationId), user);
    }
}

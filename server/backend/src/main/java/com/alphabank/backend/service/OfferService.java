package com.alphabank.backend.service;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.security.CurrentUser;

public interface OfferService {

    List<OfferDto> getOffers(CurrentUser user, UUID applicationId);
}

package com.alphabank.backend.service;

import java.util.List;

import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.OfferDto;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.security.CurrentUser;

public interface ApplicationDetailsService {

    ApplicationDetailsDto build(ApplicationRecord application, CurrentUser user);

    List<OfferDto> offers(ApplicationRecord application, CurrentUser user);
}

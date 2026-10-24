package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.dto.request.SpotCreateRequest;
import com.alphabank.backend.dto.request.SpotReviewRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.security.CurrentUser;

public interface UnderwriterSpotService {

    ApplicationDetailsDto add(CurrentUser user, UUID applicationId, SpotCreateRequest request);

    ApplicationDetailsDto review(CurrentUser user, UUID applicationId, UUID spotId, SpotReviewRequest request);
}

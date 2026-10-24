package com.alphabank.backend.service;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.SpotDto;
import com.alphabank.backend.security.CurrentUser;

public interface SpotService {

    List<SpotDto> getSpots(CurrentUser user, UUID applicationId);

    ApplicationDetailsDto close(CurrentUser user, UUID applicationId, UUID spotId, SpotCloseRequest request);
}

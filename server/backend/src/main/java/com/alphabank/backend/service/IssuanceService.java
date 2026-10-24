package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.dto.request.AcceptRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.security.CurrentUser;

public interface IssuanceService {

    ApplicationDetailsDto accept(CurrentUser user, UUID applicationId, AcceptRequest request);

    ApplicationDetailsDto sign(CurrentUser user, UUID applicationId);
}

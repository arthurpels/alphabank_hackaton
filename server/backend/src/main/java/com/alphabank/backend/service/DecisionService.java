package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.dto.request.DecisionRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.security.CurrentUser;

public interface DecisionService {

    ApplicationDetailsDto decide(CurrentUser user, UUID applicationId, DecisionRequest request);
}

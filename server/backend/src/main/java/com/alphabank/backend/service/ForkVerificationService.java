package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.dto.response.ForkVerificationDto;
import com.alphabank.backend.security.CurrentUser;

public interface ForkVerificationService {

    ForkVerificationDto verify(CurrentUser user, UUID applicationId);
}

package com.alphabank.backend.service;

import java.util.UUID;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.TransitionInitiator;

public interface StatusTransitionService {

    ApplicationStatus start(UUID applicationId, UUID userId);

    ApplicationStatus apply(UUID applicationId, ApplicationStatus to, TransitionInitiator initiator,
                            UUID userId, String reason);
}

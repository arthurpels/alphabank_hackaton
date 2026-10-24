package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.dto.request.SpotCreateRequest;
import com.alphabank.backend.dto.request.SpotReviewRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.SpotDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationService;
import com.alphabank.backend.service.SpotService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;
import com.alphabank.backend.stub.StubApplications;
import com.alphabank.backend.validator.SpotCloseRequestValidator;
import com.alphabank.backend.validator.SpotReviewRequestValidator;

@Service
public class SpotServiceImpl implements SpotService {

    private final SpotCloseRequestValidator closeValidator;
    private final SpotReviewRequestValidator reviewValidator;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationService applicationService;

    public SpotServiceImpl(SpotCloseRequestValidator closeValidator, SpotReviewRequestValidator reviewValidator,
                           ApplicationStateMachine stateMachine, ApplicationService applicationService) {
        this.closeValidator = closeValidator;
        this.reviewValidator = reviewValidator;
        this.stateMachine = stateMachine;
        this.applicationService = applicationService;
    }

    @Override
    public List<SpotDto> getSpots(CurrentUser user, UUID applicationId) {
        return applicationService.getApplication(user, applicationId).spots();
    }

    @Override
    public ApplicationDetailsDto close(CurrentUser user, UUID applicationId, UUID spotId, SpotCloseRequest request) {
        user.requireRole(UserRole.CLIENT);
        closeValidator.validate(request);
        return details(applicationId, ApplicationStatus.COLLECTING, user);
    }

    @Override
    public ApplicationDetailsDto add(CurrentUser user, UUID applicationId, SpotCreateRequest request) {
        user.requireRole(UserRole.UNDERWRITER);
        return details(applicationId, ApplicationStatus.INFO_REQUESTED, user);
    }

    @Override
    public ApplicationDetailsDto review(CurrentUser user, UUID applicationId, UUID spotId, SpotReviewRequest request) {
        user.requireRole(UserRole.UNDERWRITER);
        reviewValidator.validate(request);
        return details(applicationId, ApplicationStatus.UNDERWRITING, user);
    }

    private ApplicationDetailsDto details(UUID applicationId, ApplicationStatus status, CurrentUser user) {
        return StubApplications.coffee(applicationId, status, user.role(),
                stateMachine.availableActions(status, user.role()));
    }
}

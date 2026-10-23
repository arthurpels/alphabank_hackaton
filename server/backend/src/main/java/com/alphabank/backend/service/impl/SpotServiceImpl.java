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
import com.alphabank.backend.service.SpotService;
import com.alphabank.backend.stub.StubApplications;
import com.alphabank.backend.stub.StubSpots;
import com.alphabank.backend.validator.SpotCloseRequestValidator;
import com.alphabank.backend.validator.SpotReviewRequestValidator;

@Service
public class SpotServiceImpl implements SpotService {

    private final SpotCloseRequestValidator closeValidator;
    private final SpotReviewRequestValidator reviewValidator;

    public SpotServiceImpl(SpotCloseRequestValidator closeValidator, SpotReviewRequestValidator reviewValidator) {
        this.closeValidator = closeValidator;
        this.reviewValidator = reviewValidator;
    }

    @Override
    public List<SpotDto> getSpots(CurrentUser user, UUID applicationId) {
        return StubSpots.forStatus(user.isUnderwriter() ? ApplicationStatus.UNDERWRITING : ApplicationStatus.COLLECTING);
    }

    @Override
    public ApplicationDetailsDto close(CurrentUser user, UUID applicationId, UUID spotId, SpotCloseRequest request) {
        user.requireRole(UserRole.CLIENT);
        closeValidator.validate(request);
        return StubApplications.coffee(applicationId, ApplicationStatus.COLLECTING, user.role());
    }

    @Override
    public ApplicationDetailsDto add(CurrentUser user, UUID applicationId, SpotCreateRequest request) {
        user.requireRole(UserRole.UNDERWRITER);
        return StubApplications.coffee(applicationId, ApplicationStatus.INFO_REQUESTED, user.role());
    }

    @Override
    public ApplicationDetailsDto review(CurrentUser user, UUID applicationId, UUID spotId, SpotReviewRequest request) {
        user.requireRole(UserRole.UNDERWRITER);
        reviewValidator.validate(request);
        return StubApplications.coffee(applicationId, ApplicationStatus.UNDERWRITING, user.role());
    }
}

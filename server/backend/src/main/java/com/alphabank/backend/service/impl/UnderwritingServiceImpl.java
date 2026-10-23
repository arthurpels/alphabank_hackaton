package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.request.DecisionRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.QueueItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.UnderwritingService;
import com.alphabank.backend.stub.StubApplications;
import com.alphabank.backend.stub.StubUnderwriting;
import com.alphabank.backend.validator.DecisionRequestValidator;

@Service
public class UnderwritingServiceImpl implements UnderwritingService {

    private final DecisionRequestValidator decisionValidator;

    public UnderwritingServiceImpl(DecisionRequestValidator decisionValidator) {
        this.decisionValidator = decisionValidator;
    }

    @Override
    public List<QueueItemDto> getQueue(CurrentUser user, ApplicationStatus status) {
        user.requireRole(UserRole.UNDERWRITER);
        return StubUnderwriting.queue().stream()
                .filter(item -> status == null || item.status() == status)
                .toList();
    }

    @Override
    public UnderwritingSummaryDto getSummary(CurrentUser user, UUID applicationId) {
        user.requireRole(UserRole.UNDERWRITER);
        return StubUnderwriting.summary();
    }

    @Override
    public ApplicationDetailsDto decide(CurrentUser user, UUID applicationId, DecisionRequest request) {
        user.requireRole(UserRole.UNDERWRITER);
        decisionValidator.validate(request);
        ApplicationStatus next = switch (request.type()) {
            case APPROVED -> ApplicationStatus.APPROVED;
            case ALTERNATIVE -> ApplicationStatus.COUNTER_OFFER;
            case REJECTED -> ApplicationStatus.REJECTED;
        };
        return StubApplications.coffee(applicationId, next, user.role());
    }
}

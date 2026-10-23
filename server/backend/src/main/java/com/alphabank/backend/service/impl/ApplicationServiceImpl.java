package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.request.AcceptRequest;
import com.alphabank.backend.dto.request.ApplicationCreateRequest;
import com.alphabank.backend.dto.request.ApplicationUpdateRequest;
import com.alphabank.backend.dto.request.CancelRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.ApplicationSummaryDto;
import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationService;
import com.alphabank.backend.service.CatalogService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;
import com.alphabank.backend.stub.StubApplications;
import com.alphabank.backend.stub.StubForks;
import com.alphabank.backend.stub.StubIds;
import com.alphabank.backend.stub.StubUnderwriting;
import com.alphabank.backend.validator.ApplicationParamsValidator;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private final CatalogService catalogService;
    private final ApplicationParamsValidator paramsValidator;
    private final ApplicationStateMachine stateMachine;

    public ApplicationServiceImpl(CatalogService catalogService, ApplicationParamsValidator paramsValidator,
                                  ApplicationStateMachine stateMachine) {
        this.catalogService = catalogService;
        this.paramsValidator = paramsValidator;
        this.stateMachine = stateMachine;
    }

    @Override
    public List<ApplicationSummaryDto> getApplications(CurrentUser user) {
        user.requireRole(UserRole.CLIENT);
        return List.of(StubApplications.coffeeSummary());
    }

    @Override
    public ApplicationDetailsDto create(CurrentUser user, ApplicationCreateRequest request) {
        user.requireRole(UserRole.CLIENT);
        paramsValidator.validate(catalogService.getProduct(request.productCode()), request.amount(),
                request.termMonths());
        return details(StubIds.COFFEE_APPLICATION_ID,
                ApplicationStatus.DRAFT, user);
    }

    @Override
    public ApplicationDetailsDto getApplication(CurrentUser user, UUID applicationId) {
        ApplicationStatus status = user.isUnderwriter() ? ApplicationStatus.UNDERWRITING : ApplicationStatus.COLLECTING;
        return details(applicationId, status, user);
    }

    @Override
    public ApplicationDetailsDto update(CurrentUser user, UUID applicationId, ApplicationUpdateRequest request) {
        user.requireRole(UserRole.CLIENT);
        ProductCode productCode = request.productCode() != null ? request.productCode() : ProductCode.EQUIPMENT;
        paramsValidator.validate(catalogService.getProduct(productCode), request.amount(), request.termMonths());
        return details(applicationId, ApplicationStatus.COLLECTING, user);
    }

    @Override
    public ApplicationDetailsDto evaluate(CurrentUser user, UUID applicationId) {
        return clientAction(user, applicationId, ApplicationStatus.COLLECTING);
    }

    @Override
    public List<ForkDto> getForkHistory(CurrentUser user, UUID applicationId) {
        return StubForks.history();
    }

    @Override
    public ApplicationDetailsDto submit(CurrentUser user, UUID applicationId) {
        return clientAction(user, applicationId, ApplicationStatus.APPROVED);
    }

    @Override
    public ApplicationDetailsDto accept(CurrentUser user, UUID applicationId, AcceptRequest request) {
        return clientAction(user, applicationId, ApplicationStatus.SIGNING);
    }

    @Override
    public ApplicationDetailsDto sign(CurrentUser user, UUID applicationId) {
        return clientAction(user, applicationId, ApplicationStatus.ISSUED);
    }

    @Override
    public ApplicationDetailsDto cancel(CurrentUser user, UUID applicationId, CancelRequest request) {
        return clientAction(user, applicationId, ApplicationStatus.CANCELLED);
    }

    @Override
    public List<AuditEventDto> getHistory(CurrentUser user, UUID applicationId) {
        return StubUnderwriting.history();
    }

    private ApplicationDetailsDto clientAction(CurrentUser user, UUID applicationId, ApplicationStatus result) {
        user.requireRole(UserRole.CLIENT);
        return details(applicationId, result, user);
    }

    private ApplicationDetailsDto details(UUID applicationId, ApplicationStatus status, CurrentUser user) {
        return StubApplications.coffee(applicationId, status, user.role(),
                stateMachine.availableActions(status, user.role()));
    }
}

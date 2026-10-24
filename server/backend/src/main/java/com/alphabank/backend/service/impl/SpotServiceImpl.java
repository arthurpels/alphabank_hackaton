package com.alphabank.backend.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.closer.SpotCloseContext;
import com.alphabank.backend.closer.SpotCloser;
import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.dto.request.SpotCreateRequest;
import com.alphabank.backend.dto.request.SpotReviewRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.SpotDto;
import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.ApplicationService;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.SpotService;
import com.alphabank.backend.service.StatusTransitionService;
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
    private final ApplicationAccessService accessService;
    private final ProductRepository productRepository;
    private final SpotRepository spotRepository;
    private final ForkCalculationService forkCalculationService;
    private final StatusTransitionService statusTransitionService;
    private final Map<CloseMethod, SpotCloser> closers;

    public SpotServiceImpl(SpotCloseRequestValidator closeValidator, SpotReviewRequestValidator reviewValidator,
                           ApplicationStateMachine stateMachine, ApplicationService applicationService,
                           ApplicationAccessService accessService, ProductRepository productRepository,
                           SpotRepository spotRepository, ForkCalculationService forkCalculationService,
                           StatusTransitionService statusTransitionService, List<SpotCloser> closers) {
        this.closeValidator = closeValidator;
        this.reviewValidator = reviewValidator;
        this.stateMachine = stateMachine;
        this.applicationService = applicationService;
        this.accessService = accessService;
        this.productRepository = productRepository;
        this.spotRepository = spotRepository;
        this.forkCalculationService = forkCalculationService;
        this.statusTransitionService = statusTransitionService;
        this.closers = closers.stream().collect(Collectors.toMap(SpotCloser::method, Function.identity()));
    }

    @Override
    public List<SpotDto> getSpots(CurrentUser user, UUID applicationId) {
        return applicationService.getApplication(user, applicationId).spots();
    }

    @Override
    @Transactional
    public ApplicationDetailsDto close(CurrentUser user, UUID applicationId, UUID spotId, SpotCloseRequest request) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.CLOSE_SPOT, user.role());
        SpotRecord spot = spotRepository.findById(spotId)
                .filter(found -> found.applicationId().equals(applicationId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Пятно " + spotId + " не найдено в заявке"));
        if (spot.status() != SpotStatus.OPEN) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "Пятно уже закрыто или передано на проверку", application.status());
        }
        if (spot.closeMethod() == null) {
            throw ApiException.validation("Это пятно закрыть нельзя: неустранимый стоп-фактор");
        }
        if (spot.closeMethod() != request.method()) {
            throw ApiException.validation("Это пятно закрывается способом " + spot.closeMethod());
        }
        closeValidator.validate(request);

        ProductRecord product = productRepository.findById(application.productId())
                .orElseThrow(() -> new IllegalStateException("Продукт заявки не найден"));
        String inn = accessService.companyInn(application);
        closers.get(request.method()).close(new SpotCloseContext(application, product, inn, spot, request, user));

        forkCalculationService.save(applicationId,
                forkCalculationService.calculate(application, product, inn).score());
        moveStatusAfterClose(application, user);
        return applicationService.getApplication(user, applicationId);
    }

    @Override
    public ApplicationDetailsDto add(CurrentUser user, UUID applicationId, SpotCreateRequest request) {
        user.requireRole(UserRole.UNDERWRITER);
        return stub(applicationId, ApplicationStatus.INFO_REQUESTED, user);
    }

    @Override
    public ApplicationDetailsDto review(CurrentUser user, UUID applicationId, UUID spotId, SpotReviewRequest request) {
        user.requireRole(UserRole.UNDERWRITER);
        reviewValidator.validate(request);
        return stub(applicationId, ApplicationStatus.UNDERWRITING, user);
    }

    private void moveStatusAfterClose(ApplicationRecord application, CurrentUser user) {
        if (application.status() == ApplicationStatus.COLLECTING) {
            statusTransitionService.apply(application.id(), ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT,
                    user.id(), "Закрыто пятно");
            return;
        }
        boolean underwriterSpotsOpen = spotRepository.findByApplicationId(application.id()).stream()
                .anyMatch(spot -> spot.addedBy() == DecidedBy.UNDERWRITER && spot.status() == SpotStatus.OPEN);
        if (!underwriterSpotsOpen) {
            statusTransitionService.apply(application.id(), ApplicationStatus.UNDERWRITING,
                    TransitionInitiator.SYSTEM, null, "Клиент ответил на дозапрос");
        }
    }

    private ApplicationDetailsDto stub(UUID applicationId, ApplicationStatus status, CurrentUser user) {
        return StubApplications.coffee(applicationId, status, user.role(),
                stateMachine.availableActions(status, user.role()));
    }
}

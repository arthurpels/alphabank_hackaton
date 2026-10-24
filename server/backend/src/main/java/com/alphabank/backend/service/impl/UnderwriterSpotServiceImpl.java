package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.closer.SpotResolutionWriter;
import com.alphabank.backend.decision.GridOfferWriter;
import com.alphabank.backend.dto.request.SpotCreateRequest;
import com.alphabank.backend.dto.request.SpotReviewRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.SpotType;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.ApplicationDetailsService;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.service.UnderwriterSpotService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;
import com.alphabank.backend.validator.SpotCreateRequestValidator;
import com.alphabank.backend.validator.SpotReviewRequestValidator;

@Service
public class UnderwriterSpotServiceImpl implements UnderwriterSpotService {

    private static final Set<String> REVENUE_FACTORS = Set.of(FactorStateServiceImpl.DEBT_LOAD,
            FactorStateServiceImpl.REVENUE_STABILITY);

    private final SpotCreateRequestValidator createValidator;
    private final SpotReviewRequestValidator reviewValidator;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationAccessService accessService;
    private final ApplicationDetailsService detailsService;
    private final ApplicationRepository applicationRepository;
    private final ProductRepository productRepository;
    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;
    private final SpotResolutionWriter writer;
    private final ForkCalculationService forkCalculationService;
    private final GridOfferWriter gridOfferWriter;
    private final StatusTransitionService statusTransitionService;
    private final Clock clock;

    public UnderwriterSpotServiceImpl(SpotCreateRequestValidator createValidator,
                                      SpotReviewRequestValidator reviewValidator,
                                      ApplicationStateMachine stateMachine, ApplicationAccessService accessService,
                                      ApplicationDetailsService detailsService,
                                      ApplicationRepository applicationRepository,
                                      ProductRepository productRepository, SpotRepository spotRepository,
                                      SpotAnswerRepository spotAnswerRepository, SpotResolutionWriter writer,
                                      ForkCalculationService forkCalculationService, GridOfferWriter gridOfferWriter,
                                      StatusTransitionService statusTransitionService, Clock clock) {
        this.createValidator = createValidator;
        this.reviewValidator = reviewValidator;
        this.stateMachine = stateMachine;
        this.accessService = accessService;
        this.detailsService = detailsService;
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
        this.writer = writer;
        this.forkCalculationService = forkCalculationService;
        this.gridOfferWriter = gridOfferWriter;
        this.statusTransitionService = statusTransitionService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApplicationDetailsDto add(CurrentUser user, UUID applicationId, SpotCreateRequest request) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.ADD_SPOT, user.role());
        createValidator.validate(request);
        accessService.registerUser(user);

        Double price = request.scoringFactor() == null ? null
                : ForkCalculationServiceImpl.spotPrices(calculate(application).score()).get(request.scoringFactor());
        OffsetDateTime now = OffsetDateTime.now(clock);
        writer.create(new SpotRecord(UUID.randomUUID(), applicationId, request.type(), request.title(),
                request.clientHint(), request.scoringFactor(), price, request.closeMethod(), SpotStatus.OPEN,
                DecidedBy.UNDERWRITER, null, null, List.of(), now, null), user.id(), "Дозапрос: " + request.title());
        applicationRepository.markInfoRequested(applicationId, now);
        statusTransitionService.apply(applicationId, ApplicationStatus.INFO_REQUESTED, TransitionInitiator.UNDERWRITER,
                user.id(), "Дозапрос: " + request.title());
        return detailsService.build(accessService.load(user, applicationId), user);
    }

    @Override
    @Transactional
    public ApplicationDetailsDto review(CurrentUser user, UUID applicationId, UUID spotId,
                                        SpotReviewRequest request) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.REVIEW_SPOT, user.role());
        reviewValidator.validate(request);
        SpotRecord spot = spotRepository.findById(spotId)
                .filter(found -> found.applicationId().equals(applicationId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Пятно " + spotId + " не найдено в заявке"));
        SpotAnswerRecord answer = spotAnswerRepository.findLatestBySpotIds(List.of(spotId)).get(spotId);
        if (spot.status() != SpotStatus.ON_REVIEW || answer == null || answer.verdict() != AnswerVerdict.PENDING) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION, "Пятно не ждёт проверки", application.status());
        }
        accessService.registerUser(user);

        writer.review(spot, answer, request.verdict(), user.id(), request.comment());
        if (request.verdict() == AnswerVerdict.ACCEPTED && answer.contractAmount() != null) {
            closeRevenueSpots(applicationId, user.id());
        }
        ForkCalculation calculation = calculate(application);
        forkCalculationService.save(applicationId, calculation.request(), calculation.score());
        gridOfferWriter.save(applicationId, calculation.request(), OfferStatus.CALCULATED);
        statusTransitionService.apply(applicationId, ApplicationStatus.UNDERWRITING, TransitionInitiator.UNDERWRITER,
                user.id(), request.verdict() == AnswerVerdict.ACCEPTED
                        ? "Ответ по пятну принят: " + spot.title()
                        : "Ответ по пятну не принят: " + request.comment());
        return detailsService.build(accessService.load(user, applicationId), user);
    }

    private void closeRevenueSpots(UUID applicationId, UUID userId) {
        spotRepository.findByApplicationId(applicationId).stream()
                .filter(spot -> spot.type() == SpotType.NO_DATA && spot.status() == SpotStatus.OPEN)
                .filter(spot -> REVENUE_FACTORS.contains(spot.scoringFactor()))
                .forEach(spot -> writer.close(spot, userId, "Выручка подтверждена договором"));
    }

    private ForkCalculation calculate(ApplicationRecord application) {
        ProductRecord product = productRepository.findById(application.productId())
                .orElseThrow(() -> new IllegalStateException("Продукт заявки не найден"));
        return forkCalculationService.calculate(application, product, accessService.companyInn(application));
    }
}

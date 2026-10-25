package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.decision.DecisionWriter;
import com.alphabank.backend.decision.GridOfferWriter;
import com.alphabank.backend.decision.OfferWriter;
import com.alphabank.backend.dto.request.DecisionRequest;
import com.alphabank.backend.dto.request.ManualOfferRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.mapper.ApplicationDetailsMapper;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.OfferRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.ApplicationDetailsService;
import com.alphabank.backend.service.AuditService;
import com.alphabank.backend.service.DecisionService;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;
import com.alphabank.backend.validator.ApplicationParamsValidator;
import com.alphabank.backend.validator.DecisionRequestValidator;

@Service
public class DecisionServiceImpl implements DecisionService {

    private final DecisionRequestValidator decisionValidator;
    private final ApplicationParamsValidator paramsValidator;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationAccessService accessService;
    private final ApplicationDetailsService detailsService;
    private final ProductRepository productRepository;
    private final SpotRepository spotRepository;
    private final OfferRepository offerRepository;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final ForkCalculationService forkCalculationService;
    private final GridOfferWriter gridOfferWriter;
    private final OfferWriter offerWriter;
    private final DecisionWriter decisionWriter;
    private final AuditService auditService;
    private final StatusTransitionService statusTransitionService;
    private final ApplicationDetailsMapper mapper;
    private final Clock clock;

    public DecisionServiceImpl(DecisionRequestValidator decisionValidator, ApplicationParamsValidator paramsValidator,
                               ApplicationStateMachine stateMachine, ApplicationAccessService accessService,
                               ApplicationDetailsService detailsService, ProductRepository productRepository,
                               SpotRepository spotRepository, OfferRepository offerRepository,
                               ForkSnapshotRepository forkSnapshotRepository,
                               ForkCalculationService forkCalculationService, GridOfferWriter gridOfferWriter,
                               OfferWriter offerWriter, DecisionWriter decisionWriter, AuditService auditService,
                               StatusTransitionService statusTransitionService, ApplicationDetailsMapper mapper,
                               Clock clock) {
        this.decisionValidator = decisionValidator;
        this.paramsValidator = paramsValidator;
        this.stateMachine = stateMachine;
        this.accessService = accessService;
        this.detailsService = detailsService;
        this.productRepository = productRepository;
        this.spotRepository = spotRepository;
        this.offerRepository = offerRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.forkCalculationService = forkCalculationService;
        this.gridOfferWriter = gridOfferWriter;
        this.offerWriter = offerWriter;
        this.decisionWriter = decisionWriter;
        this.auditService = auditService;
        this.statusTransitionService = statusTransitionService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApplicationDetailsDto decide(CurrentUser user, UUID applicationId, DecisionRequest request) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.DECIDE, user.role());
        decisionValidator.validate(request);
        accessService.registerUser(user);

        List<UUID> offerIds = switch (request.type()) {
            case APPROVED -> List.of(approveRequested(application).id());
            case ALTERNATIVE -> markOffers(application, request, user);
            case REJECTED -> {
                requireSpot(application, request.reasonSpotId());
                yield List.of();
            }
        };
        ForkSnapshotRecord snapshot = forkSnapshotRepository.findLatest(applicationId).orElse(null);
        decisionWriter.byUnderwriter(applicationId, request.type(), user.id(), snapshot, request.override(),
                request.reasonSpotId(), request.comment(), offerIds);
        ApplicationStatus target = switch (request.type()) {
            case APPROVED -> ApplicationStatus.APPROVED;
            case ALTERNATIVE -> ApplicationStatus.COUNTER_OFFER;
            case REJECTED -> ApplicationStatus.REJECTED;
        };
        statusTransitionService.apply(applicationId, target, TransitionInitiator.UNDERWRITER, user.id(),
                request.comment() == null ? "Решение андеррайтера: " + request.type() : request.comment());
        return detailsService.build(accessService.load(user, applicationId), user);
    }

    private OfferRecord approveRequested(ApplicationRecord application) {
        ProductRecord product = product(application.productId());
        ForkCalculation calculation = forkCalculationService.calculate(application, product,
                accessService.companyInn(application));
        return offerWriter.save(application.id(), product, application.amount(), application.termMonths(), null,
                calculation.request(), calculation.score(), OfferStatus.OFFERED, now());
    }

    private List<UUID> markOffers(ApplicationRecord application, DecisionRequest request, CurrentUser user) {
        List<UUID> marked = new ArrayList<>();
        List<OfferRecord> offers = offerRepository.findByApplicationId(application.id());
        for (UUID offerId : request.offerIds() == null ? List.<UUID>of() : request.offerIds()) {
            OfferRecord offer = offers.stream()
                    .filter(found -> found.id().equals(offerId) && found.status() == OfferStatus.CALCULATED)
                    .findFirst()
                    .orElseThrow(() -> ApiException.validation(
                            "Вариант " + offerId + " не найден среди проходящих вариантов заявки"));
            offerRepository.updateStatus(offer.id(), OfferStatus.OFFERED);
            marked.add(offer.id());
        }
        if (request.manualOffer() != null) {
            marked.add(manualOffer(application, request.manualOffer(), request.comment(), user).id());
        }
        return marked;
    }

    private OfferRecord manualOffer(ApplicationRecord application, ManualOfferRequest manual, String comment,
                                    CurrentUser user) {
        ProductRecord product = productRepository.findByCode(manual.productCode())
                .orElseThrow(() -> ApiException.validation("Неизвестный продукт " + manual.productCode()));
        paramsValidator.validate(mapper.toProduct(product), manual.amount(), manual.termMonths());
        ForkCalculation calculation = forkCalculationService.calculate(application, product(application.productId()),
                accessService.companyInn(application));
        ScoreRequest offerRequest = gridOfferWriter.offerRequest(calculation.request(), manual.productCode(),
                manual.amount(), manual.termMonths(), manual.collateralType());
        OfferRecord offer = offerWriter.save(application.id(), product, manual.amount(), manual.termMonths(),
                manual.collateralType(), offerRequest, gridOfferWriter.score(offerRequest),
                DecidedBy.UNDERWRITER, comment, OfferStatus.OFFERED, now());
        ProductRecord requested = product(application.productId());
        auditService.record(application.id(), AuditEventType.OVERRIDE, user.id(), comment, Map.of(
                "offerId", offer.id().toString(),
                "before", conditions(requested, application.amount(), application.termMonths(), null),
                "after", conditions(product, manual.amount(), manual.termMonths(), manual.collateralType())));
        return offer;
    }

    private void requireSpot(ApplicationRecord application, UUID spotId) {
        spotRepository.findById(spotId)
                .filter(spot -> spot.applicationId().equals(application.id()))
                .orElseThrow(() -> ApiException.validation("Пятно-причина " + spotId + " не найдено в заявке"));
    }

    private ProductRecord product(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Продукт заявки не найден"));
    }

    private static Map<String, Object> conditions(ProductRecord product, long amount, int termMonths,
                                                  String collateralType) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("productCode", product.code().name());
        conditions.put("amount", amount);
        conditions.put("termMonths", termMonths);
        conditions.put("collateralType", collateralType);
        return conditions;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}

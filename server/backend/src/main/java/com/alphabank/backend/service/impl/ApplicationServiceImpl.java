package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.dto.request.ApplicationCreateRequest;
import com.alphabank.backend.dto.request.ApplicationUpdateRequest;
import com.alphabank.backend.dto.request.CancelRequest;
import com.alphabank.backend.dto.request.ConsentsRequest;
import com.alphabank.backend.dto.response.ApplicationDetailsDto;
import com.alphabank.backend.dto.response.ApplicationSummaryDto;
import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.dto.response.ForkDto;
import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.enrichment.model.SpotDraft;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.DecidedBy;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.mapper.ApplicationDetailsMapper;
import com.alphabank.backend.model.ForkCalculation;
import com.alphabank.backend.repository.AppUserRepository;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.OfferRepository;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.ApplicationDetailsService;
import com.alphabank.backend.service.ApplicationService;
import com.alphabank.backend.service.AuditService;
import com.alphabank.backend.service.AutoCheckService;
import com.alphabank.backend.service.DemoUserService;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;
import com.alphabank.backend.validator.ApplicationParamsValidator;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final SpotRepository spotRepository;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final OfferRepository offerRepository;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final AppUserRepository appUserRepository;
    private final DemoUserService demoUserService;
    private final ApplicationAccessService accessService;
    private final EnrichmentService enrichmentService;
    private final ForkCalculationService forkCalculationService;
    private final AutoCheckService autoCheckService;
    private final ApplicationDetailsService detailsService;
    private final StatusTransitionService statusTransitionService;
    private final AuditService auditService;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationParamsValidator paramsValidator;
    private final ApplicationDetailsMapper mapper;
    private final Clock clock;

    public ApplicationServiceImpl(ApplicationRepository applicationRepository, ProductRepository productRepository,
                                  CompanyRepository companyRepository, SpotRepository spotRepository,
                                  ForkSnapshotRepository forkSnapshotRepository, OfferRepository offerRepository,
                                  ApplicationSourceRepository applicationSourceRepository,
                                  AppUserRepository appUserRepository, DemoUserService demoUserService,
                                  ApplicationAccessService accessService, EnrichmentService enrichmentService,
                                  ForkCalculationService forkCalculationService, AutoCheckService autoCheckService,
                                  ApplicationDetailsService detailsService,
                                  StatusTransitionService statusTransitionService, AuditService auditService,
                                  ApplicationStateMachine stateMachine, ApplicationParamsValidator paramsValidator,
                                  ApplicationDetailsMapper mapper, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.spotRepository = spotRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.offerRepository = offerRepository;
        this.applicationSourceRepository = applicationSourceRepository;
        this.appUserRepository = appUserRepository;
        this.demoUserService = demoUserService;
        this.accessService = accessService;
        this.enrichmentService = enrichmentService;
        this.forkCalculationService = forkCalculationService;
        this.autoCheckService = autoCheckService;
        this.detailsService = detailsService;
        this.statusTransitionService = statusTransitionService;
        this.auditService = auditService;
        this.stateMachine = stateMachine;
        this.paramsValidator = paramsValidator;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public List<ApplicationSummaryDto> getApplications(CurrentUser user) {
        user.requireRole(UserRole.CLIENT);
        return accessService.resolveCompanyId(user)
                .map(applicationRepository::findByCompanyId)
                .orElse(List.of())
                .stream()
                .map(application -> mapper.toSummary(application, productById(application.productId()),
                        forkSnapshotRepository.findLatest(application.id()).orElse(null)))
                .toList();
    }

    @Override
    @Transactional
    public ApplicationDetailsDto create(CurrentUser user, ApplicationCreateRequest request) {
        user.requireRole(UserRole.CLIENT);
        requirePersonalDataConsent(request.consents());
        ProductRecord product = productByCode(request.productCode());
        paramsValidator.validate(mapper.toProduct(product), request.amount(), request.termMonths());

        MeDto me = demoUserService.findById(user.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Пользователь не найден"));
        appUserRepository.ensureExists(me);
        UUID companyId = companyRepository.findByInn(me.company().inn())
                .map(CompanyRecord::id)
                .orElseThrow(() -> new IllegalStateException("Компания пользователя не создана"));

        ApplicationRecord application = new ApplicationRecord(UUID.randomUUID(), companyId, user.id(), product.id(),
                request.amount(), request.termMonths(), request.goal(), ApplicationStatus.DRAFT,
                null, OffsetDateTime.now(clock), null, null);
        applicationRepository.insert(application);
        statusTransitionService.start(application.id(), user.id());
        enrichmentService.recordConsent(application.id(), SourceCode.BKI, request.consents().bki());
        return details(application, user);
    }

    @Override
    public ApplicationDetailsDto getApplication(CurrentUser user, UUID applicationId) {
        return details(accessService.load(user, applicationId), user);
    }

    @Override
    @Transactional
    public ApplicationDetailsDto update(CurrentUser user, UUID applicationId, ApplicationUpdateRequest request) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.EDIT, user.role());

        ProductRecord product = request.productCode() != null
                ? productByCode(request.productCode()) : productById(application.productId());
        long amount = request.amount() != null ? request.amount() : application.amount();
        int term = request.termMonths() != null ? request.termMonths() : application.termMonths();
        String goal = request.goal() != null ? request.goal() : application.goal();
        paramsValidator.validate(mapper.toProduct(product), amount, term);
        if (request.consents() != null) {
            requirePersonalDataConsent(request.consents());
            enrichmentService.recordConsent(applicationId, SourceCode.BKI, request.consents().bki());
        }
        applicationRepository.updateParameters(applicationId, product.id(), amount, term, goal);

        ApplicationRecord updated = accessService.load(user, applicationId);
        if (updated.status() == ApplicationStatus.COLLECTING) {
            ForkCalculation calculation = forkCalculationService.calculate(updated, product,
                    accessService.companyInn(updated));
            forkCalculationService.save(applicationId, calculation.request(), calculation.score());
            statusTransitionService.apply(applicationId, ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT,
                    user.id(), "Изменены параметры заявки");
        }
        return details(updated, user);
    }

    @Override
    @Transactional
    public ApplicationDetailsDto evaluate(CurrentUser user, UUID applicationId) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.EVALUATE, user.role());
        if (!applicationSourceRepository.isConsentGiven(applicationId, SourceCode.BKI)) {
            throw ApiException.validation("Для расчёта нужно согласие на запрос в БКИ");
        }

        ProductRecord product = productById(application.productId());
        ForkCalculation calculation = forkCalculationService.calculate(application, product,
                accessService.companyInn(application));
        enrichmentService.persistCompanySnapshot(calculation.enrichment().egrul());

        List<SpotDraft> drafts = calculation.enrichment().spots();
        Map<String, Double> prices = ForkCalculationServiceImpl.spotPrices(calculation.score());
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (int i = 0; i < drafts.size(); i++) {
            SpotDraft draft = drafts.get(i);
            Double price = draft.scoringFactor() == null ? draft.pricePp()
                    : prices.getOrDefault(draft.scoringFactor(), draft.pricePp());
            spotRepository.insert(new SpotRecord(UUID.randomUUID(), applicationId, draft.type(), draft.title(),
                    draft.clientHint(), draft.scoringFactor(), price, draft.closeMethod(), SpotStatus.OPEN,
                    DecidedBy.SYSTEM, draft.source(), draft.removable(), List.of(), now.plusNanos(i * 1_000L),
                    null));
        }
        forkCalculationService.save(applicationId, calculation.request(), calculation.score());
        statusTransitionService.apply(applicationId, ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT,
                user.id(), "Рассчитать");
        auditService.record(applicationId, AuditEventType.SPOT_CREATED, null,
                "Найдено пятен: " + drafts.size(), null);
        return details(accessService.load(user, applicationId), user);
    }

    @Override
    public List<ForkDto> getForkHistory(CurrentUser user, UUID applicationId) {
        accessService.load(user, applicationId);
        return forkSnapshotRepository.findByApplicationId(applicationId).stream().map(mapper::toFork).toList();
    }

    @Override
    @Transactional
    public ApplicationDetailsDto submit(CurrentUser user, UUID applicationId) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.SUBMIT, user.role());

        statusTransitionService.apply(applicationId, ApplicationStatus.SUBMITTED, TransitionInitiator.CLIENT,
                user.id(), "Отправить заявку");
        applicationRepository.markSubmitted(applicationId, OffsetDateTime.now(clock));
        statusTransitionService.apply(applicationId, ApplicationStatus.AUTO_CHECK, TransitionInitiator.SYSTEM,
                null, "Автопроверка");
        autoCheckService.run(application, productById(application.productId()),
                accessService.companyInn(application));
        return details(accessService.load(user, applicationId), user);
    }

    @Override
    @Transactional
    public ApplicationDetailsDto cancel(CurrentUser user, UUID applicationId, CancelRequest request) {
        ApplicationRecord application = accessService.load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.CANCEL, user.role());
        offerRepository.declineOffered(applicationId);
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "Клиент отменил заявку" : request.reason();
        statusTransitionService.apply(applicationId, ApplicationStatus.CANCELLED, TransitionInitiator.CLIENT,
                user.id(), reason);
        return details(accessService.load(user, applicationId), user);
    }

    @Override
    public List<AuditEventDto> getHistory(CurrentUser user, UUID applicationId) {
        accessService.load(user, applicationId);
        return auditService.getHistory(applicationId);
    }

    private ApplicationDetailsDto details(ApplicationRecord application, CurrentUser user) {
        return detailsService.build(application, user);
    }

    private ProductRecord productByCode(ProductCode code) {
        return productRepository.findByCode(code)
                .orElseThrow(() -> ApiException.validation("Неизвестный продукт " + code));
    }

    private ProductRecord productById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Продукт заявки не найден"));
    }

    private static void requirePersonalDataConsent(ConsentsRequest consents) {
        if (!Boolean.TRUE.equals(consents.personalData())) {
            throw ApiException.validation("Без согласия на обработку персональных данных заявку не создать");
        }
    }
}

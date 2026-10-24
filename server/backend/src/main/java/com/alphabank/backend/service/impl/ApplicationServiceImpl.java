package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.dto.request.AcceptRequest;
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
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.Factor;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationService;
import com.alphabank.backend.service.AuditService;
import com.alphabank.backend.service.DemoUserService;
import com.alphabank.backend.service.ForkCalculationService;
import com.alphabank.backend.service.StatusTransitionService;
import com.alphabank.backend.statemachine.ApplicationStateMachine;
import com.alphabank.backend.stub.StubApplications;
import com.alphabank.backend.validator.ApplicationParamsValidator;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final SpotRepository spotRepository;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final AppUserRepository appUserRepository;
    private final DemoUserService demoUserService;
    private final EnrichmentService enrichmentService;
    private final ForkCalculationService forkCalculationService;
    private final StatusTransitionService statusTransitionService;
    private final AuditService auditService;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationParamsValidator paramsValidator;
    private final ApplicationDetailsMapper mapper;
    private final Clock clock;

    public ApplicationServiceImpl(ApplicationRepository applicationRepository, ProductRepository productRepository,
                                  CompanyRepository companyRepository, SpotRepository spotRepository,
                                  ForkSnapshotRepository forkSnapshotRepository,
                                  ApplicationSourceRepository applicationSourceRepository,
                                  AppUserRepository appUserRepository, DemoUserService demoUserService,
                                  EnrichmentService enrichmentService, ForkCalculationService forkCalculationService,
                                  StatusTransitionService statusTransitionService, AuditService auditService,
                                  ApplicationStateMachine stateMachine, ApplicationParamsValidator paramsValidator,
                                  ApplicationDetailsMapper mapper, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.spotRepository = spotRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.applicationSourceRepository = applicationSourceRepository;
        this.appUserRepository = appUserRepository;
        this.demoUserService = demoUserService;
        this.enrichmentService = enrichmentService;
        this.forkCalculationService = forkCalculationService;
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
        return resolveCompanyId(user)
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
                OffsetDateTime.now(clock), null, null);
        applicationRepository.insert(application);
        statusTransitionService.start(application.id(), user.id());
        enrichmentService.recordConsent(application.id(), SourceCode.BKI, request.consents().bki());
        return details(application, user);
    }

    @Override
    public ApplicationDetailsDto getApplication(CurrentUser user, UUID applicationId) {
        return details(load(user, applicationId), user);
    }

    @Override
    @Transactional
    public ApplicationDetailsDto update(CurrentUser user, UUID applicationId, ApplicationUpdateRequest request) {
        ApplicationRecord application = load(user, applicationId);
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

        ApplicationRecord updated = load(user, applicationId);
        if (updated.status() == ApplicationStatus.COLLECTING) {
            ForkCalculation calculation = forkCalculationService.calculate(updated, product, companyInn(updated));
            refreshSpotPrices(applicationId, calculation.score());
            saveFork(applicationId, calculation.score());
            statusTransitionService.apply(applicationId, ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT,
                    user.id(), "Изменены параметры заявки");
        }
        return details(updated, user);
    }

    @Override
    @Transactional
    public ApplicationDetailsDto evaluate(CurrentUser user, UUID applicationId) {
        ApplicationRecord application = load(user, applicationId);
        stateMachine.checkAction(application.status(), ApplicationAction.EVALUATE, user.role());
        if (!applicationSourceRepository.isConsentGiven(applicationId, SourceCode.BKI)) {
            throw ApiException.validation("Для расчёта нужно согласие на запрос в БКИ");
        }

        ProductRecord product = productById(application.productId());
        ForkCalculation calculation = forkCalculationService.calculate(application, product,
                companyInn(application));
        enrichmentService.persistCompanySnapshot(calculation.enrichment().egrul());

        List<SpotDraft> drafts = calculation.enrichment().spots();
        Map<String, Double> prices = spotPrices(calculation.score());
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (int i = 0; i < drafts.size(); i++) {
            SpotDraft draft = drafts.get(i);
            Double price = draft.scoringFactor() == null ? draft.pricePp()
                    : prices.getOrDefault(draft.scoringFactor(), draft.pricePp());
            spotRepository.insert(new SpotRecord(UUID.randomUUID(), applicationId, draft.type(), draft.title(),
                    draft.clientHint(), draft.scoringFactor(), price, draft.closeMethod(), SpotStatus.OPEN,
                    DecidedBy.SYSTEM, draft.source(), draft.removable(), now.plusNanos(i * 1_000L), null));
        }
        saveFork(applicationId, calculation.score());
        statusTransitionService.apply(applicationId, ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT,
                user.id(), "Рассчитать");
        auditService.record(applicationId, AuditEventType.SPOT_CREATED, null,
                "Найдено пятен: " + drafts.size(), null);
        return details(load(user, applicationId), user);
    }

    @Override
    public List<ForkDto> getForkHistory(CurrentUser user, UUID applicationId) {
        load(user, applicationId);
        return forkSnapshotRepository.findByApplicationId(applicationId).stream().map(mapper::toFork).toList();
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
        load(user, applicationId);
        return auditService.getHistory(applicationId);
    }

    private ApplicationRecord load(CurrentUser user, UUID applicationId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Заявка " + applicationId + " не найдена"));
        if (!user.isUnderwriter()
                && !resolveCompanyId(user).map(id -> id.equals(application.companyId())).orElse(false)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Заявка принадлежит другой компании");
        }
        return application;
    }

    private ApplicationDetailsDto details(ApplicationRecord application, CurrentUser user) {
        CompanyRecord company = companyRepository.findById(application.companyId())
                .orElseThrow(() -> new IllegalStateException("Компания заявки не найдена"));
        return mapper.toDetails(application, company, productById(application.productId()),
                applicationSourceRepository.isConsentGiven(application.id(), SourceCode.BKI),
                forkSnapshotRepository.findLatest(application.id()).orElse(null),
                spotRepository.findByApplicationId(application.id()),
                stateMachine.availableActions(application.status(), user.role()));
    }

    private Optional<UUID> resolveCompanyId(CurrentUser user) {
        return demoUserService.findById(user.id())
                .map(MeDto::company)
                .filter(Objects::nonNull)
                .flatMap(company -> companyRepository.findByInn(company.inn()))
                .map(CompanyRecord::id);
    }

    private String companyInn(ApplicationRecord application) {
        return companyRepository.findById(application.companyId())
                .map(CompanyRecord::inn)
                .orElseThrow(() -> new IllegalStateException("Компания заявки не найдена"));
    }

    private ProductRecord productByCode(ProductCode code) {
        return productRepository.findByCode(code)
                .orElseThrow(() -> ApiException.validation("Неизвестный продукт " + code));
    }

    private ProductRecord productById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Продукт заявки не найден"));
    }

    private void saveFork(UUID applicationId, ScoreResponse score) {
        forkSnapshotRepository.insert(new ForkSnapshotRecord(UUID.randomUUID(), applicationId, score.rateFrom(),
                score.rateTo(), score.thresholds().approveMax(), score.thresholds().rejectMin(),
                score.rulesVersion(), OffsetDateTime.now(clock)));
    }

    private void refreshSpotPrices(UUID applicationId, ScoreResponse score) {
        Map<String, Double> prices = spotPrices(score);
        spotRepository.findByApplicationId(applicationId).stream()
                .filter(spot -> spot.status() == SpotStatus.OPEN && spot.scoringFactor() != null)
                .filter(spot -> prices.containsKey(spot.scoringFactor()))
                .forEach(spot -> spotRepository.updatePrice(spot.id(), prices.get(spot.scoringFactor())));
    }

    private static Map<String, Double> spotPrices(ScoreResponse score) {
        return score.factors().stream()
                .filter(factor -> factor.spotPricePp() != null)
                .collect(Collectors.toMap(factor -> factor.code().name().toLowerCase(), Factor::spotPricePp));
    }

    private static void requirePersonalDataConsent(ConsentsRequest consents) {
        if (!Boolean.TRUE.equals(consents.personalData())) {
            throw ApiException.validation("Без согласия на обработку персональных данных заявку не создать");
        }
    }

    private ApplicationDetailsDto clientAction(CurrentUser user, UUID applicationId, ApplicationStatus result) {
        user.requireRole(UserRole.CLIENT);
        return StubApplications.coffee(applicationId, result, user.role(),
                stateMachine.availableActions(result, user.role()));
    }
}

package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.QueueItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.mapper.ApplicationDetailsMapper;
import com.alphabank.backend.mapper.SummaryRequestMapper;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.SummaryClient;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.UnderwritingService;

@Service
public class UnderwritingServiceImpl implements UnderwritingService {

    private static final Set<ApplicationStatus> QUEUE = Set.of(ApplicationStatus.UNDERWRITING,
            ApplicationStatus.INFO_REQUESTED);

    private final ApplicationAccessService accessService;
    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final EnrichmentService enrichmentService;
    private final SummaryClient summaryClient;
    private final SummaryRequestMapper summaryMapper;
    private final ApplicationDetailsMapper mapper;
    private final Clock clock;

    public UnderwritingServiceImpl(ApplicationAccessService accessService,
                                   ApplicationRepository applicationRepository, CompanyRepository companyRepository,
                                   ProductRepository productRepository, ForkSnapshotRepository forkSnapshotRepository,
                                   SpotRepository spotRepository, SpotAnswerRepository spotAnswerRepository,
                                   ApplicationSourceRepository applicationSourceRepository,
                                   EnrichmentService enrichmentService, SummaryClient summaryClient,
                                   SummaryRequestMapper summaryMapper, ApplicationDetailsMapper mapper, Clock clock) {
        this.accessService = accessService;
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.productRepository = productRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
        this.applicationSourceRepository = applicationSourceRepository;
        this.enrichmentService = enrichmentService;
        this.summaryClient = summaryClient;
        this.summaryMapper = summaryMapper;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public List<QueueItemDto> getQueue(CurrentUser user, List<ApplicationStatus> statuses) {
        user.requireRole(UserRole.UNDERWRITER);
        Set<ApplicationStatus> requested = statuses == null || statuses.isEmpty() ? QUEUE : Set.copyOf(statuses);
        Comparator<ApplicationRecord> bySubmission = Comparator.comparing(ApplicationRecord::submittedAt);
        return applicationRepository.findByStatuses(requested).stream()
                .filter(application -> application.submittedAt() != null)
                .sorted(QUEUE.containsAll(requested) ? bySubmission : bySubmission.reversed())
                .map(this::toQueueItem)
                .toList();
    }

    @Override
    public UnderwritingSummaryDto getSummary(CurrentUser user, UUID applicationId) {
        user.requireRole(UserRole.UNDERWRITER);
        ApplicationRecord application = accessService.load(user, applicationId);
        ForkSnapshotRecord fork = forkSnapshotRepository.findLatest(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRANSITION,
                        "Сводка появляется после расчёта вилки", application.status()));
        List<SpotRecord> spots = spotRepository.findByApplicationId(applicationId);
        Map<UUID, SpotAnswerRecord> answers = spotAnswerRepository.findLatestBySpotIds(
                spots.stream().map(SpotRecord::id).toList());
        EnrichmentResult enrichment = enrichmentService.enrich(accessService.companyInn(application), true,
                LocalDate.now(clock));
        List<RevenueSourceData> connected = enrichment.availableRevenueSources().stream()
                .filter(source -> RevenueSeriesServiceImpl.REVENUE_SOURCES.contains(source.sourceCode()))
                .filter(source -> applicationSourceRepository.isConsentGiven(applicationId, source.sourceCode()))
                .toList();
        return summaryClient.summarize(summaryMapper.toRequest(application, product(application), fork, spots,
                answers, enrichment, connected));
    }

    private QueueItemDto toQueueItem(ApplicationRecord application) {
        CompanyRecord company = companyRepository.findById(application.companyId())
                .orElseThrow(() -> new IllegalStateException("Компания заявки не найдена"));
        List<SpotRecord> spots = spotRepository.findByApplicationId(application.id());
        return new QueueItemDto(application.id(), company.name(), company.inn(), product(application).code(),
                application.amount(), application.termMonths(), application.status(),
                forkSnapshotRepository.findLatest(application.id()).map(mapper::toFork).orElse(null),
                count(spots, SpotStatus.OPEN), count(spots, SpotStatus.ON_REVIEW), application.submittedAt());
    }

    private ProductRecord product(ApplicationRecord application) {
        return productRepository.findById(application.productId())
                .orElseThrow(() -> new IllegalStateException("Продукт заявки не найден"));
    }

    private static int count(List<SpotRecord> spots, SpotStatus status) {
        return (int) spots.stream().filter(spot -> spot.status() == status).count();
    }
}

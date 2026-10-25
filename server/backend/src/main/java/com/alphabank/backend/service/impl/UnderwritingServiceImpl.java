package com.alphabank.backend.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.QueueItemDto;
import com.alphabank.backend.dto.response.UnderwritingSummaryResponseDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.mapper.ApplicationDetailsMapper;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.SummaryGenerationService;
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
    private final SummaryGenerationService summaryGenerationService;
    private final ApplicationDetailsMapper mapper;

    public UnderwritingServiceImpl(ApplicationAccessService accessService,
                                   ApplicationRepository applicationRepository, CompanyRepository companyRepository,
                                   ProductRepository productRepository, ForkSnapshotRepository forkSnapshotRepository,
                                   SpotRepository spotRepository, SummaryGenerationService summaryGenerationService,
                                   ApplicationDetailsMapper mapper) {
        this.accessService = accessService;
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.productRepository = productRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.spotRepository = spotRepository;
        this.summaryGenerationService = summaryGenerationService;
        this.mapper = mapper;
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
    public UnderwritingSummaryResponseDto getSummary(CurrentUser user, UUID applicationId) {
        user.requireRole(UserRole.UNDERWRITER);
        accessService.load(user, applicationId);
        return summaryGenerationService.get(applicationId);
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

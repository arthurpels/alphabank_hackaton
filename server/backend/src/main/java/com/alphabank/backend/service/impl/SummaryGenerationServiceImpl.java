package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.alphabank.backend.dto.response.UnderwritingSummaryResponseDto;
import com.alphabank.backend.enrichment.dto.RevenueSourceData;
import com.alphabank.backend.enrichment.model.EnrichmentResult;
import com.alphabank.backend.enrichment.service.EnrichmentService;
import com.alphabank.backend.enums.SummaryStatus;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.mapper.SummaryRequestMapper;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.ApplicationSourceRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.ForkSnapshotRepository;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.UnderwritingSummaryRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ForkSnapshotRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;
import com.alphabank.backend.service.SummaryGenerationService;

@Service
public class SummaryGenerationServiceImpl implements SummaryGenerationService {

    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final ForkSnapshotRepository forkSnapshotRepository;
    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;
    private final ApplicationSourceRepository applicationSourceRepository;
    private final EnrichmentService enrichmentService;
    private final SummaryRequestMapper summaryMapper;
    private final TemplateSummaryFactory templateFactory;
    private final UnderwritingSummaryRepository summaryRepository;
    private final SummaryLlmGenerator llmGenerator;
    private final Clock clock;
    private final boolean llmEnabled;

    public SummaryGenerationServiceImpl(ApplicationRepository applicationRepository, CompanyRepository companyRepository,
                                        ProductRepository productRepository, ForkSnapshotRepository forkSnapshotRepository,
                                        SpotRepository spotRepository, SpotAnswerRepository spotAnswerRepository,
                                        ApplicationSourceRepository applicationSourceRepository,
                                        EnrichmentService enrichmentService, SummaryRequestMapper summaryMapper,
                                        TemplateSummaryFactory templateFactory,
                                        UnderwritingSummaryRepository summaryRepository,
                                        SummaryLlmGenerator llmGenerator, Clock clock,
                                        @Value("${app.summary.llm-enabled}") boolean llmEnabled) {
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.productRepository = productRepository;
        this.forkSnapshotRepository = forkSnapshotRepository;
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
        this.applicationSourceRepository = applicationSourceRepository;
        this.enrichmentService = enrichmentService;
        this.summaryMapper = summaryMapper;
        this.templateFactory = templateFactory;
        this.summaryRepository = summaryRepository;
        this.llmGenerator = llmGenerator;
        this.clock = clock;
        this.llmEnabled = llmEnabled;
    }

    @Override
    public UnderwritingSummaryResponseDto get(UUID applicationId) {
        return summaryRepository.findByApplicationId(applicationId)
                .map(record -> new UnderwritingSummaryResponseDto(record.status(), record.generatedBy(), record.items(),
                        record.llm(), record.generatedAt()))
                .orElseGet(() -> refresh(applicationId));
    }

    @Override
    public UnderwritingSummaryResponseDto refresh(UUID applicationId) {
        SummaryRequest request = request(applicationId);
        var items = templateFactory.create(request);
        SummaryStatus status = llmEnabled ? SummaryStatus.GENERATING : SummaryStatus.READY;
        OffsetDateTime now = OffsetDateTime.now(clock);
        long revision = summaryRepository.saveTemplate(applicationId, status, items, now);
        if (llmEnabled) {
            generateAfterCommit(applicationId, revision, request);
        }
        return new UnderwritingSummaryResponseDto(status, "TEMPLATE", items, null, now);
    }

    private SummaryRequest request(UUID applicationId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Заявка не найдена"));
        ProductRecord product = productRepository.findById(application.productId())
                .orElseThrow(() -> new IllegalStateException("Продукт заявки не найден"));
        ForkSnapshotRecord fork = forkSnapshotRepository.findLatest(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRANSITION,
                        "Сводка появляется после расчёта вилки", application.status()));
        List<SpotRecord> spots = spotRepository.findByApplicationId(applicationId);
        Map<UUID, SpotAnswerRecord> answers = spotAnswerRepository.findLatestBySpotIds(
                spots.stream().map(SpotRecord::id).toList());
        String inn = companyRepository.findById(application.companyId())
                .orElseThrow(() -> new IllegalStateException("Компания заявки не найдена"))
                .inn();
        EnrichmentResult enrichment = enrichmentService.enrich(inn, true, application.asOf());
        List<RevenueSourceData> connected = enrichment.availableRevenueSources().stream()
                .filter(source -> RevenueSeriesServiceImpl.REVENUE_SOURCES.contains(source.sourceCode()))
                .filter(source -> applicationSourceRepository.isConsentGiven(applicationId, source.sourceCode()))
                .toList();
        return summaryMapper.toRequest(application, product, fork, spots, answers, enrichment, connected);
    }

    private void generateAfterCommit(UUID applicationId, long revision, SummaryRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    llmGenerator.generate(applicationId, revision, request);
                }
            });
        } else {
            llmGenerator.generate(applicationId, revision, request);
        }
    }
}

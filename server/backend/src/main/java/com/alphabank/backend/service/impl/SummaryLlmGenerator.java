package com.alphabank.backend.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.UnderwritingSummaryDto;
import com.alphabank.backend.repository.UnderwritingSummaryRepository;
import com.alphabank.backend.scoringclient.SummaryClient;
import com.alphabank.backend.scoringclient.dto.SummaryRequest;

@Component
public class SummaryLlmGenerator {

    private static final Logger log = LoggerFactory.getLogger(SummaryLlmGenerator.class);

    private final SummaryClient summaryClient;
    private final UnderwritingSummaryRepository summaryRepository;
    private final Clock clock;

    public SummaryLlmGenerator(SummaryClient summaryClient, UnderwritingSummaryRepository summaryRepository, Clock clock) {
        this.summaryClient = summaryClient;
        this.summaryRepository = summaryRepository;
        this.clock = clock;
    }

    @Async
    public void generate(UUID applicationId, long revision, SummaryRequest request) {
        try {
            UnderwritingSummaryDto summary = summaryClient.summarize(request);
            summaryRepository.complete(applicationId, revision, summary.generatedBy(), summary.items(), summary.llm(),
                    OffsetDateTime.now(clock));
        } catch (RuntimeException e) {
            log.warn("LLM-сводка недоступна, сохраняем шаблонную: {}", e.getMessage());
            summaryRepository.markReady(applicationId, revision, OffsetDateTime.now(clock));
        }
    }
}

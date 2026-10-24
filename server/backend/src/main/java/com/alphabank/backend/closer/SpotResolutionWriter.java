package com.alphabank.backend.closer;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.repository.SpotAnswerRepository;
import com.alphabank.backend.repository.SpotRepository;
import com.alphabank.backend.repository.model.SpotAnswerRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.service.AuditService;

@Component
public class SpotResolutionWriter {

    private static final String SYSTEM = "SYSTEM";

    private final SpotRepository spotRepository;
    private final SpotAnswerRepository spotAnswerRepository;
    private final AuditService auditService;
    private final Clock clock;

    public SpotResolutionWriter(SpotRepository spotRepository, SpotAnswerRepository spotAnswerRepository,
                                AuditService auditService, Clock clock) {
        this.spotRepository = spotRepository;
        this.spotAnswerRepository = spotAnswerRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    public void close(SpotRecord spot, UUID userId, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotRepository.updateStatus(spot.id(), SpotStatus.CLOSED, now);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CLOSED, userId, reason,
                Map.of("spotId", spot.id().toString()));
    }

    public void acceptAnswer(SpotRecord spot, UUID userId, String optionCode, String text, UUID documentId,
                             String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, documentId,
                AnswerVerdict.ACCEPTED, SYSTEM, null, now, now));
        close(spot, userId, reason);
    }

    public void sendToReview(SpotRecord spot, UUID userId, String optionCode, String text, UUID documentId) {
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, documentId,
                AnswerVerdict.PENDING, null, null, OffsetDateTime.now(clock), null));
        spotRepository.updateStatus(spot.id(), SpotStatus.ON_REVIEW, null);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CLOSED, userId,
                "Ответ передан на проверку специалисту",
                Map.of("spotId", spot.id().toString(), "status", SpotStatus.ON_REVIEW.name()));
    }

    public void rejectAnswer(SpotRecord spot, UUID userId, String optionCode, String text, String comment) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, null,
                AnswerVerdict.REJECTED, SYSTEM, comment, now, now));
        auditService.record(spot.applicationId(), AuditEventType.SPOT_REVIEWED, userId, comment,
                Map.of("spotId", spot.id().toString(), "verdict", AnswerVerdict.REJECTED.name()));
    }

    public void create(SpotRecord spot, String reason) {
        spotRepository.insert(spot);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CREATED, null, reason,
                Map.of("spotId", spot.id().toString()));
    }
}

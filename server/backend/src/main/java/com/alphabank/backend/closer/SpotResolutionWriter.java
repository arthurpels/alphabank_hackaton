package com.alphabank.backend.closer;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.dto.response.AnomalyVerdictDto;
import com.alphabank.backend.dto.response.PurposeVerdictDto;
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
        close(spot, userId, reason, Map.of("spotId", spot.id().toString()));
    }

    private void close(SpotRecord spot, UUID userId, String reason, Map<String, Object> payload) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotRepository.updateStatus(spot.id(), SpotStatus.CLOSED, now);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CLOSED, userId, reason, payload);
    }

    public void acceptPurpose(SpotRecord spot, UUID userId, String optionCode, String goal,
                              PurposeVerdictDto verdict, boolean reused, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, goal, null,
                null, null, AnswerVerdict.ACCEPTED, SYSTEM, null, now, now, verdict, null));
        close(spot, userId, reason, purposePayload(spot, verdict, reused, AnswerVerdict.ACCEPTED));
    }

    public void rejectPurpose(SpotRecord spot, UUID userId, String goal, PurposeVerdictDto verdict, boolean reused,
                              String comment, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), null, goal, null,
                null, null, AnswerVerdict.REJECTED, SYSTEM, comment, now, now, verdict, null));
        auditService.record(spot.applicationId(), AuditEventType.SPOT_REVIEWED, userId, reason,
                purposePayload(spot, verdict, reused, AnswerVerdict.REJECTED));
    }

    public void sendPurposeToReview(SpotRecord spot, UUID userId, String goal, PurposeVerdictDto verdict,
                                    boolean reused, String reason) {
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), null, goal, null,
                null, null, AnswerVerdict.PENDING, null, null, OffsetDateTime.now(clock), null, verdict, null));
        spotRepository.updateStatus(spot.id(), SpotStatus.ON_REVIEW, null);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CLOSED, userId, reason,
                purposePayload(spot, verdict, reused, AnswerVerdict.PENDING));
    }

    private static Map<String, Object> purposePayload(SpotRecord spot, PurposeVerdictDto verdict, boolean reused,
                                                      AnswerVerdict answerVerdict) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spotId", spot.id().toString());
        payload.put("verdict", answerVerdict.name());
        payload.put("purposeStatus", verdict.status().name());
        payload.put("generatedBy", verdict.generatedBy());
        payload.put("reused", reused);
        if (verdict.llm() != null) {
            payload.put("llm", Map.of("model", verdict.llm().model(), "digest", verdict.llm().digest(),
                    "promptVersion", verdict.llm().promptVersion(), "cached", verdict.llm().cached()));
        }
        return payload;
    }

    public void acceptAnomalyAnswer(SpotRecord spot, UUID userId, String optionCode, String text,
                                    AnomalyVerdictDto verdict, boolean reused, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, null,
                null, null, AnswerVerdict.ACCEPTED, SYSTEM, null, now, now, null, verdict));
        close(spot, userId, reason, anomalyPayload(spot, verdict, reused, AnswerVerdict.ACCEPTED));
    }

    public void sendAnomalyAnswerToReview(SpotRecord spot, UUID userId, String optionCode, String text,
                                          AnomalyVerdictDto verdict, boolean reused, String reason) {
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, null,
                null, null, AnswerVerdict.PENDING, null, null, OffsetDateTime.now(clock), null, null, verdict));
        spotRepository.updateStatus(spot.id(), SpotStatus.ON_REVIEW, null);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CLOSED, userId, reason,
                anomalyPayload(spot, verdict, reused, AnswerVerdict.PENDING));
    }

    private static Map<String, Object> anomalyPayload(SpotRecord spot, AnomalyVerdictDto verdict, boolean reused,
                                                      AnswerVerdict answerVerdict) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spotId", spot.id().toString());
        payload.put("verdict", answerVerdict.name());
        payload.put("answerCode", verdict.code() == null ? null : verdict.code().name());
        if (verdict.classification() != null) {
            payload.put("classifyStatus", verdict.classification().status().name());
            payload.put("generatedBy", verdict.classification().generatedBy());
            payload.put("violation", verdict.classification().violation());
            payload.put("reused", reused);
            if (verdict.classification().llm() != null) {
                payload.put("llm", Map.of("model", verdict.classification().llm().model(),
                        "digest", verdict.classification().llm().digest(),
                        "promptVersion", verdict.classification().llm().promptVersion(),
                        "cached", verdict.classification().llm().cached()));
            }
        }
        if (verdict.check() != null) {
            payload.put("check", verdict.check().verdict().name());
            payload.put("rule", verdict.check().rule());
        }
        return payload;
    }

    public void acceptAnswer(SpotRecord spot, UUID userId, String optionCode, String text, UUID documentId,
                             String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, documentId,
                null, null, AnswerVerdict.ACCEPTED, SYSTEM, null, now, now, null, null));
        close(spot, userId, reason);
    }

    public void sendToReview(SpotRecord spot, UUID userId, String optionCode, String text, UUID documentId) {
        sendToReview(spot, userId, optionCode, text, documentId, null, null);
    }

    public void sendToReview(SpotRecord spot, UUID userId, String optionCode, String text, UUID documentId,
                             Long contractAmount, Integer contractTermMonths) {
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, documentId,
                contractAmount, contractTermMonths, AnswerVerdict.PENDING, null, null, OffsetDateTime.now(clock),
                null, null, null));
        spotRepository.updateStatus(spot.id(), SpotStatus.ON_REVIEW, null);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CLOSED, userId,
                "Ответ передан на проверку специалисту",
                Map.of("spotId", spot.id().toString(), "status", SpotStatus.ON_REVIEW.name()));
    }

    public void rejectAnswer(SpotRecord spot, UUID userId, String optionCode, String text, String comment) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.insert(new SpotAnswerRecord(UUID.randomUUID(), spot.id(), optionCode, text, null,
                null, null, AnswerVerdict.REJECTED, SYSTEM, comment, now, now, null, null));
        auditService.record(spot.applicationId(), AuditEventType.SPOT_REVIEWED, userId, comment,
                Map.of("spotId", spot.id().toString(), "verdict", AnswerVerdict.REJECTED.name()));
    }

    public void create(SpotRecord spot, String reason) {
        create(spot, null, reason);
    }

    public void create(SpotRecord spot, UUID userId, String reason) {
        spotRepository.insert(spot);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_CREATED, userId, reason,
                Map.of("spotId", spot.id().toString()));
    }

    public void review(SpotRecord spot, SpotAnswerRecord answer, AnswerVerdict verdict, UUID userId,
                       String comment) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        spotAnswerRepository.review(answer.id(), verdict, userId.toString(), comment, now);
        boolean accepted = verdict == AnswerVerdict.ACCEPTED;
        spotRepository.updateStatus(spot.id(), accepted ? SpotStatus.CLOSED : SpotStatus.OPEN, accepted ? now : null);
        auditService.record(spot.applicationId(), AuditEventType.SPOT_REVIEWED, userId,
                accepted ? "Ответ принят" : comment,
                Map.of("spotId", spot.id().toString(), "verdict", verdict.name()));
    }
}

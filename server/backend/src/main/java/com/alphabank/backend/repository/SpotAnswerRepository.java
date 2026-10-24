package com.alphabank.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.alphabank.backend.enums.AnswerVerdict;
import com.alphabank.backend.repository.model.SpotAnswerRecord;

public interface SpotAnswerRepository {

    void insert(SpotAnswerRecord answer);

    Map<UUID, SpotAnswerRecord> findLatestBySpotIds(List<UUID> spotIds);

    List<SpotAnswerRecord> findAcceptedContracts(UUID applicationId);

    void review(UUID id, AnswerVerdict verdict, String reviewedBy, String reviewComment, OffsetDateTime reviewedAt);
}

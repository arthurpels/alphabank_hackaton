package com.alphabank.backend.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.alphabank.backend.repository.model.SpotAnswerRecord;

public interface SpotAnswerRepository {

    void insert(SpotAnswerRecord answer);

    Map<UUID, SpotAnswerRecord> findLatestBySpotIds(List<UUID> spotIds);
}

package com.alphabank.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.enums.SpotStatus;
import com.alphabank.backend.repository.model.SpotRecord;

public interface SpotRepository {

    void insert(SpotRecord spot);

    Optional<SpotRecord> findById(UUID spotId);

    List<SpotRecord> findByApplicationId(UUID applicationId);

    void updatePrice(UUID spotId, Double pricePp);

    void updateStatus(UUID spotId, SpotStatus status, OffsetDateTime closedAt);
}

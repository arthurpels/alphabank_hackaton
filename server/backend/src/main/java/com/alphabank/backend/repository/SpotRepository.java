package com.alphabank.backend.repository;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.repository.model.SpotRecord;

public interface SpotRepository {

    void insert(SpotRecord spot);

    List<SpotRecord> findByApplicationId(UUID applicationId);

    void updatePrice(UUID spotId, Double pricePp);
}

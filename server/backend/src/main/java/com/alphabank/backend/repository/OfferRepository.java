package com.alphabank.backend.repository;

import java.util.List;
import java.util.UUID;

import com.alphabank.backend.repository.model.OfferRecord;

public interface OfferRepository {

    int expireActive(UUID applicationId);

    void insert(OfferRecord offer);

    List<OfferRecord> findByApplicationId(UUID applicationId);
}

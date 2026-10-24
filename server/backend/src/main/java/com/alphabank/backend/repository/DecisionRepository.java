package com.alphabank.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.repository.model.DecisionRecord;

public interface DecisionRepository {

    void insert(DecisionRecord decision);

    Optional<DecisionRecord> findByApplicationId(UUID applicationId);
}

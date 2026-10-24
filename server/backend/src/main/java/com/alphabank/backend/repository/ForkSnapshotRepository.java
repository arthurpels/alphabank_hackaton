package com.alphabank.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.repository.model.ForkSnapshotRecord;

public interface ForkSnapshotRepository {

    void insert(ForkSnapshotRecord snapshot);

    Optional<ForkSnapshotRecord> findLatest(UUID applicationId);

    List<ForkSnapshotRecord> findByApplicationId(UUID applicationId);

    Optional<ForkSnapshotRecord> findById(UUID id);
}

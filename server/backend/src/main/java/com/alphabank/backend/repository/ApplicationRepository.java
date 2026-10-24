package com.alphabank.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.repository.model.ApplicationRecord;

public interface ApplicationRepository {

    void insert(ApplicationRecord application);

    Optional<ApplicationRecord> findById(UUID id);

    List<ApplicationRecord> findByCompanyId(UUID companyId);

    void updateParameters(UUID id, UUID productId, long amount, int termMonths, String goal);
}

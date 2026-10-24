package com.alphabank.backend.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.repository.model.ApplicationRecord;

public interface ApplicationRepository {

    void insert(ApplicationRecord application);

    Optional<ApplicationRecord> findById(UUID id);

    List<ApplicationRecord> findByCompanyId(UUID companyId);

    void markSubmitted(UUID id, OffsetDateTime submittedAt);

    void markInfoRequested(UUID id, OffsetDateTime infoRequestedAt);

    List<ApplicationRecord> findByStatuses(Set<ApplicationStatus> statuses);

    void updateParameters(UUID id, UUID productId, long amount, int termMonths, String goal);

    void fixAsOf(UUID id, LocalDate asOf);
}

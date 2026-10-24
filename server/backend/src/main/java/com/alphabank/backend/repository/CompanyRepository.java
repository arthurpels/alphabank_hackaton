package com.alphabank.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.enums.LegalForm;
import com.alphabank.backend.repository.model.CompanyRecord;

public interface CompanyRepository {

    UUID upsertCompany(String inn, String name, LegalForm legalForm, String egrulSnapshotJson);

    Optional<CompanyRecord> findByInn(String inn);

    Optional<CompanyRecord> findById(UUID id);

    void updateEgrulSnapshot(String inn, String egrulSnapshotJson);
}

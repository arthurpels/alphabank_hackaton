package com.alphabank.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.repository.model.ApplicationSourceRecord;

public interface ApplicationSourceRepository {

    void recordConsent(UUID applicationId, SourceCode sourceCode, boolean consentGiven);

    Optional<ApplicationSourceRecord> find(UUID applicationId, SourceCode sourceCode);

    List<ApplicationSourceRecord> findByApplicationId(UUID applicationId);

    boolean isConsentGiven(UUID applicationId, SourceCode sourceCode);
}

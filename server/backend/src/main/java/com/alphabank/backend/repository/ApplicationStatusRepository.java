package com.alphabank.backend.repository;

import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.enums.ApplicationStatus;

public interface ApplicationStatusRepository {

    Optional<ApplicationStatus> findStatusForUpdate(UUID applicationId);

    void updateStatus(UUID applicationId, ApplicationStatus status);
}

package com.alphabank.backend.service;

import java.util.Optional;
import java.util.UUID;

import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.security.CurrentUser;

public interface ApplicationAccessService {

    ApplicationRecord load(CurrentUser user, UUID applicationId);

    Optional<UUID> resolveCompanyId(CurrentUser user);

    String companyInn(ApplicationRecord application);

    void registerUser(CurrentUser user);
}
